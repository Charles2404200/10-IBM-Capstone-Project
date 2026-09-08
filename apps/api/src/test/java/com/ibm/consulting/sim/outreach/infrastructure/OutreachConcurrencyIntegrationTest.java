package com.ibm.consulting.sim.outreach.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.application.AiOrchestrationService;
import com.ibm.consulting.sim.ai.domain.OutreachEvaluationResult;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadDifficulty;
import com.ibm.consulting.sim.lead.domain.LeadRepository;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.outreach.application.OutreachResponse;
import com.ibm.consulting.sim.outreach.application.OutreachService;
import com.ibm.consulting.sim.outreach.domain.OutreachAttempt;
import com.ibm.consulting.sim.outreach.domain.OutreachNextAction;
import com.ibm.consulting.sim.outreach.domain.OutreachOutcome;
import com.ibm.consulting.sim.outreach.domain.OutreachRepository;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(JpaOutreachRepository.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutreachConcurrencyIntegrationTest {

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
    @Autowired OutreachRepository outreachRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void concurrentSendsAllocateOnlyTheFinalAllowedAttempt() throws Exception {
        TestData data = inTransaction(this::persistEngagementWithTwoAttempts);
        CountDownLatch savesReached = new CountDownLatch(2);
        OutreachService service = service(gateAttemptSaves(outreachRepository, savesReached), data);

        List<Outcome> outcomes = runConcurrently(
                () -> service.send(data.engagementId(), data.userId(), "Subject A", "Body A"),
                () -> service.send(data.engagementId(), data.userId(), "Subject B", "Body B"));

        List<OutreachAttempt> attempts = inTransaction(
                () -> outreachRepository.findByEngagementId(data.engagementId()));
        assertThat(attempts).hasSize(3);
        assertThat(attempts).extracting(OutreachAttempt::getAttemptNumber).doesNotHaveDuplicates();
        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
    }

    private OutreachService service(OutreachRepository attempts, TestData data) {
        AiOrchestrationService ai = mock(AiOrchestrationService.class);
        when(ai.execute(anyString(), any(UUID.class), anyString(), anyInt(), any(), any()))
                .thenReturn(OutreachEvaluationResult.safeFallback());
        DifficultyProfileService difficulty = mock(DifficultyProfileService.class);
        when(difficulty.forEngagement(any(Engagement.class))).thenReturn(DifficultyProfile.defaults(3, 3, 3, 3));
        LeadRepository leads = mock(LeadRepository.class);
        when(leads.findById(data.lead().getId())).thenReturn(Optional.of(data.lead()));
        ResearchEvidenceRepository evidence = mock(ResearchEvidenceRepository.class);
        when(evidence.findByEngagementId(data.engagementId())).thenReturn(List.of());
        return new OutreachService(attempts, new EntityManagerEngagementRepository(entityManager), ai,
                new ObjectMapper(), difficulty, leads, evidence);
    }

    private OutreachRepository gateAttemptSaves(OutreachRepository delegate, CountDownLatch savesReached) {
        return new OutreachRepository() {
            @Override public OutreachAttempt save(OutreachAttempt attempt) {
                if (attempt.getAttemptNumber() == 3) {
                    savesReached.countDown();
                    awaitPeer(savesReached);
                }
                return delegate.save(attempt);
            }
            @Override public List<OutreachAttempt> findByEngagementId(UUID engagementId) {
                return delegate.findByEngagementId(engagementId);
            }
            @Override public Optional<OutreachAttempt> findById(UUID id) { return delegate.findById(id); }
            @Override public int countByEngagementId(UUID engagementId) { return delegate.countByEngagementId(engagementId); }
        };
    }

    private List<Outcome> runConcurrently(Callable<OutreachResponse> firstCommand,
                                          Callable<OutreachResponse> secondCommand) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Outcome> first = executor.submit(() -> execute(start, firstCommand));
            Future<Outcome> second = executor.submit(() -> execute(start, secondCommand));
            start.countDown();
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
    }

    private Outcome execute(CountDownLatch start, Callable<OutreachResponse> command) throws Exception {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            return new Outcome(inTransaction(command), null);
        } catch (RuntimeException exception) {
            return new Outcome(null, exception);
        }
    }

    private void awaitPeer(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private TestData persistEngagementWithTwoAttempts() {
        User user = User.create(UUID.randomUUID() + "@example.com", "hash", "Learner", UserRole.LEARNER);
        Scenario scenario = Scenario.create("Outreach scenario", "Technology", "Scenario", 3);
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
        entityManager.persist(engagement);
        for (int sequence = 1; sequence <= 2; sequence++) {
            OutreachAttempt attempt = OutreachAttempt.create(engagement.getId(), sequence, "Subject", "Body");
            attempt.resolve("No", OutreachOutcome.REJECTED, OutreachNextAction.NONE, 10, 10, 10, 10);
            entityManager.persist(attempt);
        }
        entityManager.flush();
        return new TestData(user.getId(), engagement.getId(), lead);
    }

    private <T> T inTransaction(Callable<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try { return work.call(); }
            catch (RuntimeException exception) { throw exception; }
            catch (Exception exception) { throw new IllegalStateException(exception); }
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

    private record TestData(UUID userId, UUID engagementId, Lead lead) {}
    private record Outcome(OutreachResponse response, RuntimeException failure) {
        boolean succeeded() { return response != null; }
    }
}
