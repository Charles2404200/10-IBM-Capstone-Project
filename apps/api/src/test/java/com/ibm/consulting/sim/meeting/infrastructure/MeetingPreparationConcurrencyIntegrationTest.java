package com.ibm.consulting.sim.meeting.infrastructure;

import com.ibm.consulting.sim.assessment.domain.AssessmentRepository;
import com.ibm.consulting.sim.engagement.application.EngagementQueryService;
import com.ibm.consulting.sim.engagement.application.EngagementResponse;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadDifficulty;
import com.ibm.consulting.sim.lead.domain.LeadRepository;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.meeting.application.MeetingPreparationResponse;
import com.ibm.consulting.sim.meeting.application.MeetingPreparationService;
import com.ibm.consulting.sim.meeting.domain.MeetingPreparation;
import com.ibm.consulting.sim.meeting.domain.MeetingPreparationRepository;
import com.ibm.consulting.sim.meeting.domain.MeetingRepository;
import com.ibm.consulting.sim.scenario.domain.Persona;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(JpaMeetingPreparationRepository.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MeetingPreparationConcurrencyIntegrationTest {

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
    @Autowired MeetingPreparationRepository preparations;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void concurrentFirstUpdatesCreateOneCompletePreparationAndOneTransition() throws Exception {
        TestData data = inTransaction(this::persistMeetingSecuredEngagement);
        MeetingPreparationService service = new MeetingPreparationService(
                preparations, new EntityManagerEngagementRepository());

        List<Outcome> outcomes = runConcurrently(
                () -> service.update(data.engagementId(), data.userId(), "Validate the opportunity",
                        List.of("Set context", "Explore impact", "Confirm next step"),
                        List.of("What changed?", "Who is affected?", "How is success measured?")),
                () -> service.update(data.engagementId(), data.userId(), "Validate the opportunity",
                        List.of("Set context", "Explore impact", "Confirm next step"),
                        List.of("What changed?", "Who is affected?", "How is success measured?")));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(countPreparations(data.engagementId())).isEqualTo(1L);
        inTransaction(() -> {
            MeetingPreparation saved = preparations.findByEngagementId(data.engagementId()).orElseThrow();
            assertThat(saved.getObjective()).isEqualTo("Validate the opportunity");
            assertThat(saved.getAgenda()).containsExactly("Set context", "Explore impact", "Confirm next step");
            assertThat(saved.getDiscoveryQuestions())
                    .containsExactly("What changed?", "Who is affected?", "How is success measured?");
        });
        assertThat(inTransaction(() -> entityManager.find(Engagement.class, data.engagementId()).getState()))
                .isEqualTo(EngagementState.PREPARING);
        assertThat(countPreparingTransitions(data.engagementId())).isEqualTo(1L);
    }

    @Test
    void unrelatedEngagementPreparationsCanReachPersistenceConcurrently() throws Exception {
        TestData first = inTransaction(this::persistMeetingSecuredEngagement);
        TestData second = inTransaction(this::persistMeetingSecuredEngagement);
        CountDownLatch savesReached = new CountDownLatch(2);
        MeetingPreparationRepository gated = gateSaves(savesReached);
        MeetingPreparationService service = new MeetingPreparationService(
                gated, new EntityManagerEngagementRepository());

        List<Outcome> outcomes = runConcurrently(
                () -> service.update(first.engagementId(), first.userId(), "First", List.of(), List.of()),
                () -> service.update(second.engagementId(), second.userId(), "Second", List.of(), List.of()));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(countPreparations(first.engagementId())).isEqualTo(1L);
        assertThat(countPreparations(second.engagementId())).isEqualTo(1L);
    }

    @Test
    void engagementSaveFailureRollsBackPreparationAndLifecycleTransition() {
        TestData data = inTransaction(this::persistMeetingSecuredEngagement);
        var updatedAtBefore = inTransaction(() ->
                entityManager.find(Engagement.class, data.engagementId()).getUpdatedAt());
        MeetingPreparationService service = new MeetingPreparationService(
                preparations, engagementRepositoryFailingOnSave());

        assertThatThrownBy(() -> inTransaction(() -> service.update(
                data.engagementId(), data.userId(), "Validate the opportunity",
                List.of("Set context", "Explore impact", "Confirm next step"),
                List.of("What changed?", "Who is affected?", "How is success measured?"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated engagement persistence failure");

        assertThat(countPreparations(data.engagementId())).isZero();
        inTransaction(() -> {
            Engagement persisted = entityManager.find(Engagement.class, data.engagementId());
            assertThat(persisted.getState()).isEqualTo(EngagementState.MEETING_SECURED);
            assertThat(persisted.getUpdatedAt()).isEqualTo(updatedAtBefore);
        });
        assertThat(countPreparingTransitions(data.engagementId())).isZero();

        MeetingPreparationService retryService = new MeetingPreparationService(
                preparations, new EntityManagerEngagementRepository());
        MeetingPreparationResponse retry = inTransaction(() -> retryService.update(
                data.engagementId(), data.userId(), "Validate the opportunity",
                List.of("Set context", "Explore impact", "Confirm next step"),
                List.of("What changed?", "Who is affected?", "How is success measured?")));

        assertThat(retry.ready()).isTrue();
        assertThat(countPreparations(data.engagementId())).isEqualTo(1L);
        assertThat(inTransaction(() -> entityManager.find(Engagement.class, data.engagementId()).getState()))
                .isEqualTo(EngagementState.PREPARING);
        assertThat(countPreparingTransitions(data.engagementId())).isEqualTo(1L);

        EngagementQueryService queries = queryService();
        EngagementResponse detail = inTransaction(() ->
                queries.getWorkspace(data.engagementId(), data.userId()));
        List<EngagementResponse> dashboard = inTransaction(() -> queries.listForUser(data.userId()));
        assertThat(detail.state()).isEqualTo(EngagementState.PREPARING.name());
        assertThat(dashboard).extracting(EngagementResponse::state)
                .containsExactly(EngagementState.PREPARING.name());
    }

    private EngagementQueryService queryService() {
        ScenarioRepository scenarios = mock(ScenarioRepository.class);
        when(scenarios.findById(any())).thenAnswer(invocation -> Optional.ofNullable(
                entityManager.find(Scenario.class, invocation.getArgument(0))));
        when(scenarios.findByIdIn(any())).thenAnswer(invocation ->
                invocation.<List<UUID>>getArgument(0).stream()
                        .map(id -> entityManager.find(Scenario.class, id))
                        .filter(java.util.Objects::nonNull)
                        .toList());

        LeadRepository leads = mock(LeadRepository.class);
        when(leads.findById(any())).thenAnswer(invocation -> Optional.ofNullable(
                entityManager.find(Lead.class, invocation.getArgument(0))));
        when(leads.findByIdIn(any())).thenAnswer(invocation ->
                invocation.<List<UUID>>getArgument(0).stream()
                        .map(id -> entityManager.find(Lead.class, id))
                        .filter(java.util.Objects::nonNull)
                        .toList());

        ResearchEvidenceRepository evidence = mock(ResearchEvidenceRepository.class);
        when(evidence.countByEngagementId(any())).thenReturn(0L);
        when(evidence.countByEngagementIds(any())).thenReturn(java.util.Map.of());
        MeetingRepository meetings = mock(MeetingRepository.class);
        when(meetings.findByEngagementId(any())).thenReturn(Optional.empty());
        when(meetings.findAllByEngagementIdIn(any())).thenReturn(List.of());

        return new EngagementQueryService(new EntityManagerEngagementRepository(),
                mock(AssessmentRepository.class), scenarios, leads, evidence, meetings);
    }

    private EngagementRepository engagementRepositoryFailingOnSave() {
        EngagementRepository delegate = new EntityManagerEngagementRepository();
        return new EngagementRepository() {
            @Override public Engagement save(Engagement engagement) {
                throw new IllegalStateException("simulated engagement persistence failure");
            }
            @Override public List<Engagement> findAll() { return delegate.findAll(); }
            @Override public Optional<Engagement> findById(UUID id) { return delegate.findById(id); }
            @Override public List<Engagement> findByUserId(UUID userId) { return delegate.findByUserId(userId); }
            @Override public List<Engagement> findDashboardByUserId(UUID userId) {
                return delegate.findDashboardByUserId(userId);
            }
            @Override public Optional<Engagement> findByIdAndUserId(UUID id, UUID userId) {
                return delegate.findByIdAndUserId(id, userId);
            }
            @Override public Optional<Engagement> findByIdAndUserIdForUpdate(UUID id, UUID userId) {
                return delegate.findByIdAndUserIdForUpdate(id, userId);
            }
        };
    }

    private MeetingPreparationRepository gateSaves(CountDownLatch savesReached) {
        return new MeetingPreparationRepository() {
            @Override public MeetingPreparation save(MeetingPreparation preparation) {
                savesReached.countDown();
                try {
                    assertThat(savesReached.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return preparations.save(preparation);
            }
            @Override public Optional<MeetingPreparation> findByEngagementId(UUID engagementId) {
                return preparations.findByEngagementId(engagementId);
            }
        };
    }

    private List<Outcome> runConcurrently(Runnable first, Runnable second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Outcome> firstResult = executor.submit(() -> execute(start, first));
            Future<Outcome> secondResult = executor.submit(() -> execute(start, second));
            start.countDown();
            return List.of(firstResult.get(15, TimeUnit.SECONDS), secondResult.get(15, TimeUnit.SECONDS));
        }
    }

    private Outcome execute(CountDownLatch start, Runnable command) {
        try {
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            inTransaction(command);
            return new Outcome(true, null);
        } catch (Throwable failure) {
            return new Outcome(false, failure);
        }
    }

    private TestData persistMeetingSecuredEngagement() {
        User user = User.create(UUID.randomUUID() + "@example.com", "hash", "Learner", UserRole.LEARNER);
        Scenario scenario = Scenario.create("Preparation", "Technology", "Scenario", 3);
        Persona persona = Persona.create(scenario, "Client", "CIO", "Example Co", "Direct",
                "Risk", "Budget", "Delivery");
        Lead lead = Lead.create(scenario.getId(), "Example Co", "Technology", "Opportunity", LeadDifficulty.MEDIUM);
        entityManager.persist(user);
        entityManager.persist(scenario);
        entityManager.persist(persona);
        entityManager.persist(lead);
        Engagement engagement = Engagement.start(user.getId(), scenario.getId(), persona.getId());
        engagement.selectLead(lead.getId());
        engagement.transitionTo(EngagementState.HYPOTHESIS_READY, "ready");
        engagement.transitionTo(EngagementState.OUTREACHING, "outreach");
        engagement.transitionTo(EngagementState.MEETING_SECURED, "secured");
        entityManager.persist(engagement);
        entityManager.flush();
        return new TestData(user.getId(), engagement.getId());
    }

    private long countPreparations(UUID engagementId) {
        return inTransaction(() -> entityManager.createQuery(
                        "select count(preparation) from MeetingPreparation preparation where preparation.engagementId = :id",
                        Long.class).setParameter("id", engagementId).getSingleResult());
    }

    private long countPreparingTransitions(UUID engagementId) {
        return inTransaction(() -> entityManager.createQuery("""
                        select count(event) from EngagementEvent event
                        where event.engagement.id = :id and event.state = :state
                        """, Long.class)
                .setParameter("id", engagementId)
                .setParameter("state", EngagementState.PREPARING)
                .getSingleResult());
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> work.run());
    }

    private <T> T inTransaction(java.util.concurrent.Callable<T> work) {
        return new TransactionTemplate(transactionManager).execute(ignored -> {
            try { return work.call(); }
            catch (RuntimeException exception) { throw exception; }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        });
    }

    private final class EntityManagerEngagementRepository implements EngagementRepository {
        @Override public Engagement save(Engagement engagement) { return engagement; }
        @Override public List<Engagement> findAll() {
            return entityManager.createQuery("select engagement from Engagement engagement", Engagement.class)
                    .getResultList();
        }
        @Override public Optional<Engagement> findById(UUID id) {
            return Optional.ofNullable(entityManager.find(Engagement.class, id));
        }
        @Override public List<Engagement> findByUserId(UUID userId) {
            return entityManager.createQuery("""
                            select engagement from Engagement engagement
                            where engagement.userId = :userId order by engagement.createdAt desc
                            """, Engagement.class)
                    .setParameter("userId", userId)
                    .getResultList();
        }
        @Override public List<Engagement> findDashboardByUserId(UUID userId) { return findByUserId(userId); }
        @Override public Optional<Engagement> findByIdAndUserId(UUID id, UUID userId) {
            return findById(id).filter(engagement -> engagement.getUserId().equals(userId));
        }
        @Override public Optional<Engagement> findByIdAndUserIdForUpdate(UUID id, UUID userId) {
            Engagement engagement = entityManager.find(Engagement.class, id, LockModeType.PESSIMISTIC_WRITE);
            return Optional.ofNullable(engagement).filter(candidate -> candidate.getUserId().equals(userId));
        }
    }

    private record TestData(UUID userId, UUID engagementId) {}
    private record Outcome(boolean succeeded, Throwable failure) {}
}
