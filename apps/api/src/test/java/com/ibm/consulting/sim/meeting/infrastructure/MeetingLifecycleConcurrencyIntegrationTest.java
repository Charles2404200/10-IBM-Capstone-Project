package com.ibm.consulting.sim.meeting.infrastructure;

import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadDifficulty;
import com.ibm.consulting.sim.meeting.application.MeetingResponse;
import com.ibm.consulting.sim.meeting.application.MeetingService;
import com.ibm.consulting.sim.meeting.application.MeetingServiceTestFactory;
import com.ibm.consulting.sim.meeting.domain.Meeting;
import com.ibm.consulting.sim.meeting.domain.MeetingCompletionOutcome;
import com.ibm.consulting.sim.meeting.domain.MeetingRepository;
import com.ibm.consulting.sim.meeting.domain.PreparationNotReadyException;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import com.ibm.consulting.sim.scenario.domain.Persona;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaMeetingRepository.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MeetingLifecycleConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired EntityManager entityManager;
    @Autowired MeetingRepository meetingRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void concurrentStartsCreateExactlyOneMeeting() throws Exception {
        TestData data = inTransaction(() -> persistEngagement(EngagementState.PREPARING, false));
        CountDownLatch savesReached = new CountDownLatch(2);
        MeetingRepository gatedMeetings = gateNewMeetingSaves(meetingRepository, savesReached);
        MeetingService service = service(gatedMeetings);

        List<CommandOutcome> outcomes = runConcurrently(
                () -> service.start(data.engagementId(), data.userId()),
                () -> service.start(data.engagementId(), data.userId()));

        List<Meeting> meetings = inTransaction(
                () -> meetingRepository.findAllByEngagementIdOrderByCreatedAtAsc(data.engagementId()));
        Engagement persisted = inTransaction(() -> entityManager.find(Engagement.class, data.engagementId()));
        assertThat(meetings).hasSize(1);
        assertThat(persisted.getState()).isEqualTo(EngagementState.IN_MEETING);
        assertThat(outcomes).filteredOn(CommandOutcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .extracting(CommandOutcome::failure)
                .allMatch(PreparationNotReadyException.class::isInstance);
    }

    @Test
    void concurrentRetriesCreateOneNewAttemptAndReplayIt() throws Exception {
        TestData data = inTransaction(() -> persistEngagement(EngagementState.IN_MEETING, true));
        CountDownLatch savesReached = new CountDownLatch(2);
        MeetingRepository gatedMeetings = gateNewMeetingSaves(meetingRepository, savesReached);
        MeetingService service = service(gatedMeetings);

        List<CommandOutcome> outcomes = runConcurrently(
                () -> service.retry(data.meetingId(), data.userId()),
                () -> service.retry(data.meetingId(), data.userId()));

        List<Meeting> attempts = inTransaction(
                () -> meetingRepository.findAllByEngagementIdOrderByCreatedAtAsc(data.engagementId()));
        assertThat(attempts).hasSize(2);
        assertThat(outcomes).allMatch(CommandOutcome::succeeded);
        assertThat(outcomes.get(0).response().id()).isEqualTo(outcomes.get(1).response().id());
    }

    private MeetingService service(MeetingRepository meetings) {
        return MeetingServiceTestFactory.forLifecycleCommands(
                meetings,
                new EntityManagerEngagementRepository(entityManager),
                DifficultyProfile.defaults(3, 3, 3, 3));
    }

    private List<CommandOutcome> runConcurrently(Callable<MeetingResponse> firstCommand,
                                                  Callable<MeetingResponse> secondCommand) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<CommandOutcome> first = executor.submit(() -> execute(start, firstCommand));
            Future<CommandOutcome> second = executor.submit(() -> execute(start, secondCommand));
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
    }

    private CommandOutcome execute(CountDownLatch start, Callable<MeetingResponse> command) throws Exception {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            return new CommandOutcome(inTransaction(command), null);
        } catch (RuntimeException exception) {
            return new CommandOutcome(null, exception);
        }
    }

    private MeetingRepository gateNewMeetingSaves(MeetingRepository delegate, CountDownLatch savesReached) {
        return new MeetingRepository() {
            @Override public Meeting save(Meeting meeting) {
                savesReached.countDown();
                awaitPeer(savesReached);
                return delegate.save(meeting);
            }
            @Override public Optional<Meeting> findById(UUID id) { return delegate.findById(id); }
            @Override public Optional<Meeting> findByIdForUpdate(UUID id) { return delegate.findByIdForUpdate(id); }
            @Override public List<Meeting> findAllByEngagementIdOrderByCreatedAtAsc(UUID engagementId) {
                return delegate.findAllByEngagementIdOrderByCreatedAtAsc(engagementId);
            }
            @Override public List<Meeting> findAllByEngagementIdIn(List<UUID> ids) {
                return delegate.findAllByEngagementIdIn(ids);
            }
            @Override public Optional<Meeting> findByEngagementId(UUID engagementId) {
                return delegate.findByEngagementId(engagementId);
            }
        };
    }

    private void awaitPeer(CountDownLatch latch) {
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private TestData persistEngagement(EngagementState targetState, boolean failedMeeting) {
        User user = User.create(UUID.randomUUID() + "@example.com", "hash", "Learner", UserRole.LEARNER);
        Scenario scenario = Scenario.create("Meeting scenario", "Technology", "Scenario", 3);
        Persona persona = Persona.create(scenario, "Client", "CIO", "Example Co", "Direct", "Risk",
                "Budget", "Delivery");
        Lead lead = Lead.create(scenario.getId(), "Example Co", "Technology", "Modernisation", LeadDifficulty.MEDIUM);
        entityManager.persist(user);
        entityManager.persist(scenario);
        entityManager.persist(persona);
        entityManager.persist(lead);
        Engagement engagement = Engagement.start(user.getId(), scenario.getId(), persona.getId());
        engagement.selectLead(lead.getId());
        engagement.transitionTo(EngagementState.HYPOTHESIS_READY, "ready");
        engagement.transitionTo(EngagementState.OUTREACHING, "outreach");
        engagement.transitionTo(EngagementState.MEETING_SECURED, "secured");
        engagement.transitionTo(EngagementState.PREPARING, "preparing");
        if (targetState == EngagementState.IN_MEETING) {
            engagement.transitionTo(EngagementState.IN_MEETING, "meeting");
        }
        entityManager.persist(engagement);
        Meeting meeting = null;
        if (failedMeeting) {
            meeting = Meeting.start(engagement.getId(), persona.getId());
            meeting.complete(MeetingCompletionOutcome.FAILED, "Try again", List.of("Listen"));
            entityManager.persist(meeting);
        }
        entityManager.flush();
        return new TestData(user.getId(), engagement.getId(), meeting == null ? null : meeting.getId());
    }

    private <T> T inTransaction(Callable<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try {
                return work.call();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private final class EntityManagerEngagementRepository implements EngagementRepository {
        private final EntityManager em;
        private EntityManagerEngagementRepository(EntityManager em) { this.em = em; }
        @Override public Engagement save(Engagement engagement) { return engagement; }
        @Override public List<Engagement> findAll() { return List.of(); }
        @Override public Optional<Engagement> findById(UUID id) { return Optional.ofNullable(em.find(Engagement.class, id)); }
        @Override public List<Engagement> findByUserId(UUID userId) { return List.of(); }
        @Override public List<Engagement> findDashboardByUserId(UUID userId) { return List.of(); }
        @Override public Optional<Engagement> findByIdAndUserId(UUID id, UUID userId) {
            return findById(id).filter(engagement -> userId.equals(engagement.getUserId()));
        }
        @Override public Optional<Engagement> findByIdAndUserIdForUpdate(UUID id, UUID userId) {
            Engagement engagement = em.find(Engagement.class, id, LockModeType.PESSIMISTIC_WRITE);
            return Optional.ofNullable(engagement).filter(candidate -> userId.equals(candidate.getUserId()));
        }
    }

    private record TestData(UUID userId, UUID engagementId, UUID meetingId) {}
    private record CommandOutcome(MeetingResponse response, RuntimeException failure) {
        boolean succeeded() { return response != null; }
    }
}
