package com.ibm.consulting.sim.assessment.infrastructure;

import com.ibm.consulting.sim.achievement.application.AchievementEvaluationService;
import com.ibm.consulting.sim.assessment.application.AssessmentResponse;
import com.ibm.consulting.sim.assessment.application.AssessmentService;
import com.ibm.consulting.sim.assessment.domain.Assessment;
import com.ibm.consulting.sim.assessment.domain.AssessmentRepository;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadDifficulty;
import com.ibm.consulting.sim.meeting.domain.PersonaStateRepository;
import com.ibm.consulting.sim.outreach.domain.OutreachRepository;
import com.ibm.consulting.sim.proposal.domain.ProposalRepository;
import com.ibm.consulting.sim.scenario.domain.Persona;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AssessmentConcurrencyIntegrationTest {

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
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void concurrentGenerationReturnsOneAssessmentAndPublishesSideEffectsOnce() throws Exception {
        TestData data = inTransaction(this::persistClientDecisionEngagement);
        AchievementEvaluationService achievements = mock(AchievementEvaluationService.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        Scenario scenario = inTransaction(() -> entityManager.find(Scenario.class, data.scenarioId()));
        ScenarioRepository scenarios = mock(ScenarioRepository.class);
        when(scenarios.findById(data.scenarioId())).thenReturn(Optional.of(scenario));
        AssessmentService service = new AssessmentService(new EntityManagerAssessmentRepository(),
                new EntityManagerEngagementRepository(), mock(ResearchEvidenceRepository.class),
                mock(OutreachRepository.class), mock(PersonaStateRepository.class), mock(ProposalRepository.class),
                scenarios, achievements, events);

        List<AssessmentResponse> responses = runConcurrently(
                () -> service.generate(data.engagementId(), data.userId()),
                () -> service.generate(data.engagementId(), data.userId()));

        Long count = inTransaction(() -> entityManager.createQuery(
                        "select count(assessment) from Assessment assessment where assessment.engagementId = :id",
                        Long.class).setParameter("id", data.engagementId()).getSingleResult());
        Engagement persisted = inTransaction(() -> entityManager.find(Engagement.class, data.engagementId()));
        List<Object[]> lifecycleEvents = inTransaction(() -> entityManager.createQuery("""
                        select event.state, count(event) from EngagementEvent event
                        where event.engagement.id = :id and event.state in (:review, :completed)
                        group by event.state
                        """, Object[].class)
                .setParameter("id", data.engagementId())
                .setParameter("review", EngagementState.REVIEW)
                .setParameter("completed", EngagementState.COMPLETED)
                .getResultList());
        assertThat(count).isEqualTo(1L);
        assertThat(responses).extracting(AssessmentResponse::id).containsOnly(responses.getFirst().id());
        assertThat(persisted.getState()).isEqualTo(EngagementState.COMPLETED);
        assertThat(lifecycleEvents).extracting(row -> row[1]).containsExactlyInAnyOrder(1L, 1L);
        verify(achievements, times(1)).evaluateForUser(data.userId());
        verify(events, times(1)).publishEvent((Object) org.mockito.ArgumentMatchers.any());
    }

    private TestData persistClientDecisionEngagement() {
        User user = User.create(UUID.randomUUID() + "@example.com", "hash", "Learner", UserRole.LEARNER);
        Scenario scenario = Scenario.create("Assessment", "Technology", "Description", 3);
        Persona persona = Persona.create(scenario, "Client", "CIO", "Example", "Direct", null, null, null);
        Lead lead = Lead.create(scenario.getId(), "Example", "Technology", "Modernisation", LeadDifficulty.MEDIUM);
        entityManager.persist(user);
        entityManager.persist(scenario);
        entityManager.persist(persona);
        entityManager.persist(lead);
        Engagement engagement = Engagement.start(user.getId(), scenario.getId(), persona.getId());
        engagement.selectLead(lead.getId());
        engagement.transitionTo(EngagementState.HYPOTHESIS_READY, "ready");
        engagement.transitionTo(EngagementState.OUTREACHING, "outreach");
        engagement.transitionTo(EngagementState.MEETING_SECURED, "meeting");
        engagement.transitionTo(EngagementState.PREPARING, "preparing");
        engagement.transitionTo(EngagementState.IN_MEETING, "meeting started");
        engagement.transitionTo(EngagementState.DISCOVERY_COMPLETE, "discovery");
        engagement.transitionTo(EngagementState.PROPOSAL_DRAFT, "draft");
        engagement.transitionTo(EngagementState.PROPOSAL_SUBMITTED, "submitted");
        engagement.transitionTo(EngagementState.CLIENT_DECISION, "decision");
        entityManager.persist(engagement);
        entityManager.flush();
        return new TestData(user.getId(), scenario.getId(), engagement.getId());
    }

    private List<AssessmentResponse> runConcurrently(Callable<AssessmentResponse> first,
                                                     Callable<AssessmentResponse> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<AssessmentResponse> firstResult = executor.submit(() -> execute(start, first));
            Future<AssessmentResponse> secondResult = executor.submit(() -> execute(start, second));
            start.countDown();
            return List.of(firstResult.get(15, TimeUnit.SECONDS), secondResult.get(15, TimeUnit.SECONDS));
        }
    }

    private AssessmentResponse execute(CountDownLatch start, Callable<AssessmentResponse> command) throws Exception {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return inTransaction(command);
    }

    private <T> T inTransaction(Callable<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try { return work.call(); }
            catch (RuntimeException exception) { throw exception; }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        });
    }

    private final class EntityManagerEngagementRepository implements EngagementRepository {
        @Override public Engagement save(Engagement engagement) { return engagement; }
        @Override public List<Engagement> findAll() { return List.of(); }
        @Override public Optional<Engagement> findById(UUID id) {
            return Optional.ofNullable(entityManager.find(Engagement.class, id));
        }
        @Override public List<Engagement> findByUserId(UUID userId) { return List.of(); }
        @Override public List<Engagement> findDashboardByUserId(UUID userId) { return List.of(); }
        @Override public Optional<Engagement> findByIdAndUserId(UUID id, UUID userId) {
            return findById(id).filter(engagement -> userId.equals(engagement.getUserId()));
        }
        @Override public Optional<Engagement> findByIdAndUserIdForUpdate(UUID id, UUID userId) {
            Engagement engagement = entityManager.find(Engagement.class, id, LockModeType.PESSIMISTIC_WRITE);
            return Optional.ofNullable(engagement).filter(candidate -> userId.equals(candidate.getUserId()));
        }
    }

    private final class EntityManagerAssessmentRepository implements AssessmentRepository {
        @Override public Assessment save(Assessment assessment) {
            entityManager.persist(assessment);
            return assessment;
        }
        @Override public Optional<Assessment> findByEngagementId(UUID engagementId) {
            return entityManager.createQuery(
                            "select assessment from Assessment assessment where assessment.engagementId = :id",
                            Assessment.class)
                    .setParameter("id", engagementId)
                    .getResultStream().findFirst();
        }
        @Override public List<Assessment> findAllByEngagementIdIn(List<UUID> engagementIds) { return List.of(); }
    }

    private record TestData(UUID userId, UUID scenarioId, UUID engagementId) {}
}
