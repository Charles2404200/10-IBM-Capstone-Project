package com.ibm.consulting.sim.meeting.infrastructure;

import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadDifficulty;
import com.ibm.consulting.sim.meeting.application.MeetingService;
import com.ibm.consulting.sim.meeting.application.MeetingServiceTestFactory;
import com.ibm.consulting.sim.meeting.application.MeetingTurnResult;
import com.ibm.consulting.sim.meeting.domain.ConversationActor;
import com.ibm.consulting.sim.meeting.domain.ConversationTurn;
import com.ibm.consulting.sim.meeting.domain.ConversationTurnRepository;
import com.ibm.consulting.sim.meeting.domain.Meeting;
import com.ibm.consulting.sim.meeting.domain.MeetingRepository;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import com.ibm.consulting.sim.scenario.domain.Persona;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import jakarta.persistence.EntityManager;
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
@Import({JpaMeetingRepository.class, JpaConversationTurnRepository.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MeetingMessageConcurrencyIntegrationTest {

    private static final String CLIENT_MESSAGE_ID = "same-client-message";
    private static final String MESSAGE = "You're an idiot.";

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
    @Autowired ConversationTurnRepository turnRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void concurrentDuplicateMessagesBothReplayTheSinglePersistedTurnPair() throws Exception {
        TestData data = inTransaction(this::persistInProgressMeeting);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch learnerSavesReached = new CountDownLatch(2);
        ConversationTurnRepository gatedTurns = gateLearnerSaves(turnRepository, learnerSavesReached);
        MeetingService service = MeetingServiceTestFactory.forImmediateTermination(
                meetingRepository,
                gatedTurns,
                new EntityManagerEngagementRepository(entityManager),
                data.engagementId(),
                data.persona(),
                data.profile());

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<MeetingTurnResult> first = executor.submit(
                    () -> send(start, service, data.meetingId(), data.userId()));
            Future<MeetingTurnResult> second = executor.submit(
                    () -> send(start, service, data.meetingId(), data.userId()));
            start.countDown();

            MeetingTurnResult firstResult = first.get(10, TimeUnit.SECONDS);
            MeetingTurnResult secondResult = second.get(10, TimeUnit.SECONDS);

            List<ConversationTurn> persisted = inTransaction(
                    () -> turnRepository.findByMeetingIdOrderBySequenceAsc(data.meetingId()));
            assertThat(persisted).hasSize(2);
            assertThat(persisted).filteredOn(turn -> turn.getActor() == ConversationActor.LEARNER).hasSize(1);
            assertThat(firstResult.learnerTurn().id()).isEqualTo(secondResult.learnerTurn().id());
            assertThat(firstResult.personaTurn().id()).isEqualTo(secondResult.personaTurn().id());
        }
    }

    private MeetingTurnResult send(CountDownLatch start, MeetingService service, UUID meetingId, UUID userId)
            throws Exception {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return inTransaction(() -> service.sendMessage(meetingId, userId, MESSAGE, CLIENT_MESSAGE_ID));
    }

    private ConversationTurnRepository gateLearnerSaves(ConversationTurnRepository delegate,
                                                         CountDownLatch learnerSavesReached) {
        return new ConversationTurnRepository() {
            @Override
            public ConversationTurn save(ConversationTurn turn) {
                if (turn.getActor() == ConversationActor.LEARNER && CLIENT_MESSAGE_ID.equals(turn.getClientMessageId())) {
                    learnerSavesReached.countDown();
                    awaitPeer(learnerSavesReached);
                }
                return delegate.save(turn);
            }

            @Override public List<ConversationTurn> findByMeetingIdOrderBySequenceAsc(UUID meetingId) {
                return delegate.findByMeetingIdOrderBySequenceAsc(meetingId);
            }
            @Override public int countByMeetingId(UUID meetingId) { return delegate.countByMeetingId(meetingId); }
            @Override public Optional<ConversationTurn> findByMeetingIdAndClientMessageId(
                    UUID meetingId, String clientMessageId) {
                return delegate.findByMeetingIdAndClientMessageId(meetingId, clientMessageId);
            }
        };
    }

    private void awaitPeer(CountDownLatch latch) {
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating duplicate messages", exception);
        }
    }

    private TestData persistInProgressMeeting() {
        User user = User.create("meeting-user@example.com", "hash", "Meeting User", UserRole.LEARNER);
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
        engagement.transitionTo(EngagementState.HYPOTHESIS_READY, "Hypothesis ready");
        engagement.transitionTo(EngagementState.OUTREACHING, "Outreach started");
        engagement.transitionTo(EngagementState.MEETING_SECURED, "Meeting secured");
        engagement.transitionTo(EngagementState.PREPARING, "Preparation started");
        engagement.transitionTo(EngagementState.IN_MEETING, "Meeting started");
        entityManager.persist(engagement);

        Meeting meeting = Meeting.start(engagement.getId(), persona.getId());
        entityManager.persist(meeting);
        entityManager.flush();
        return new TestData(user.getId(), engagement.getId(), meeting.getId(), persona,
                DifficultyProfile.defaults(3, 3, 3, 3));
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

        private EntityManagerEngagementRepository(EntityManager em) {
            this.em = em;
        }

        @Override public Engagement save(Engagement engagement) { return engagement; }
        @Override public List<Engagement> findAll() {
            return em.createQuery("select engagement from Engagement engagement", Engagement.class).getResultList();
        }
        @Override public Optional<Engagement> findById(UUID id) {
            return Optional.ofNullable(em.find(Engagement.class, id));
        }
        @Override public List<Engagement> findByUserId(UUID userId) {
            return em.createQuery("select engagement from Engagement engagement where engagement.userId = :userId",
                            Engagement.class)
                    .setParameter("userId", userId)
                    .getResultList();
        }
        @Override public List<Engagement> findDashboardByUserId(UUID userId) { return findByUserId(userId); }
        @Override public Optional<Engagement> findByIdAndUserId(UUID id, UUID userId) {
            return findById(id).filter(engagement -> userId.equals(engagement.getUserId()));
        }
        @Override public Optional<Engagement> findByIdAndUserIdForUpdate(UUID id, UUID userId) {
            return findByIdAndUserId(id, userId);
        }
    }

    private record TestData(UUID userId, UUID engagementId, UUID meetingId, Persona persona,
                            DifficultyProfile profile) {}
}
