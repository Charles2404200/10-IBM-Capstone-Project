package com.ibm.consulting.sim.lead.infrastructure;

import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.lead.application.LeadService;
import com.ibm.consulting.sim.lead.application.LeadAlreadySelectedException;
import com.ibm.consulting.sim.lead.domain.ConfidenceLevel;
import com.ibm.consulting.sim.lead.domain.EvidenceOrigin;
import com.ibm.consulting.sim.lead.domain.EvidenceType;
import com.ibm.consulting.sim.lead.domain.EvidenceVerificationStatus;
import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadDifficulty;
import com.ibm.consulting.sim.lead.domain.LeadRepository;
import com.ibm.consulting.sim.lead.domain.ResearchEvidence;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.application.ScenarioAuthoringConfigService;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DataJpaTest
@Import({JpaLeadRepository.class, JpaResearchEvidenceRepository.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LeadCommandConcurrencyIntegrationTest {

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
    @Autowired LeadRepository leadRepository;
    @Autowired ResearchEvidenceRepository evidenceRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void concurrentSelectionsLockInTheFirstLead() throws Exception {
        TestData data = inTransaction(() -> persistData(false));
        CountDownLatch readsReached = new CountDownLatch(2);
        LeadService service = service(gateOrdinaryEngagementReads(readsReached), evidenceRepository);

        List<Outcome> outcomes = runConcurrently(
                () -> service.selectLead(data.engagementId(), data.firstLeadId(), data.userId()),
                () -> service.selectLead(data.engagementId(), data.secondLeadId(), data.userId()));

        Engagement persisted = inTransaction(() -> entityManager.find(Engagement.class, data.engagementId()));
        assertThat(persisted.getSelectedLeadId()).isIn(data.firstLeadId(), data.secondLeadId());
        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .extracting(Outcome::failure)
                .allMatch(LeadAlreadySelectedException.class::isInstance);
    }

    @Test
    void concurrentEvidenceSavesAllocateUniqueMonotonicSequences() throws Exception {
        TestData data = inTransaction(() -> persistData(true));
        CountDownLatch countsReached = new CountDownLatch(2);
        LeadService service = service(new EntityManagerEngagementRepository(entityManager),
                gateEvidenceCounts(evidenceRepository, countsReached));

        List<Outcome> outcomes = runConcurrently(
                () -> saveEvidence(service, data, "First evidence"),
                () -> saveEvidence(service, data, "Second evidence"));

        List<ResearchEvidence> evidence = inTransaction(
                () -> evidenceRepository.findByEngagementId(data.engagementId()));
        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(evidence).hasSize(2);
        assertThat(evidence).extracting(ResearchEvidence::getSequenceNo).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void concurrentResearchCompletionTransitionsExactlyOnce() throws Exception {
        TestData data = inTransaction(() -> {
            TestData created = persistData(true);
            persistReadyEvidence(created);
            return created;
        });
        CountDownLatch readsReached = new CountDownLatch(2);
        LeadService service = service(gateOrdinaryEngagementReads(readsReached), evidenceRepository);

        List<Outcome> outcomes = runConcurrently(
                () -> service.completeResearch(data.engagementId(), data.userId()),
                () -> service.completeResearch(data.engagementId(), data.userId()));

        Engagement persisted = inTransaction(() -> entityManager.find(Engagement.class, data.engagementId()));
        Long transitionEvents = inTransaction(() -> entityManager.createQuery("""
                        select count(event) from EngagementEvent event
                        where event.engagement.id = :engagementId and event.state = :state
                        """, Long.class)
                .setParameter("engagementId", data.engagementId())
                .setParameter("state", EngagementState.HYPOTHESIS_READY)
                .getSingleResult());
        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(persisted.getState()).isEqualTo(EngagementState.HYPOTHESIS_READY);
        assertThat(transitionEvents).isEqualTo(1L);
    }

    private void saveEvidence(LeadService service, TestData data, String note) {
        service.saveEvidence(data.engagementId(), data.userId(), note, null, EvidenceType.OTHER,
                null, null, EvidenceOrigin.USER_SUPPLIED, EvidenceVerificationStatus.UNVERIFIED,
                null, ConfidenceLevel.MEDIUM, 35, Set.of());
    }

    private void persistReadyEvidence(TestData data) {
        List<EvidenceType> types = List.of(EvidenceType.STAKEHOLDER_PROFILE,
                EvidenceType.FINANCIAL_SIGNAL, EvidenceType.TECHNOLOGY_INDICATOR, EvidenceType.HYPOTHESIS);
        for (int index = 0; index < types.size(); index++) {
            EvidenceType type = types.get(index);
            entityManager.persist(ResearchEvidence.builder()
                    .engagementId(data.engagementId())
                    .leadId(data.firstLeadId())
                    .note(type == EvidenceType.HYPOTHESIS
                            ? "The client likely needs a staged modernisation pilot grounded in the collected evidence."
                            : "Trusted scenario evidence for " + type)
                    .evidenceType(type)
                    .origin(EvidenceOrigin.SCENARIO_CURATED)
                    .verificationStatus(EvidenceVerificationStatus.CORROBORATED)
                    .confidence(ConfidenceLevel.HIGH)
                    .relevanceScore(90)
                    .sequenceNo(index + 1)
                    .build());
        }
        entityManager.flush();
    }

    private LeadService service(EngagementRepository engagements, ResearchEvidenceRepository evidence) {
        return new LeadService(leadRepository, evidence, engagements,
                mock(DifficultyProfileService.class), mock(ScenarioRepository.class),
                mock(ScenarioAuthoringConfigService.class));
    }

    private EngagementRepository gateOrdinaryEngagementReads(CountDownLatch readsReached) {
        EntityManagerEngagementRepository delegate = new EntityManagerEngagementRepository(entityManager);
        return new EntityManagerEngagementRepository(entityManager) {
            @Override public Optional<Engagement> findByIdAndUserId(UUID id, UUID userId) {
                Optional<Engagement> engagement = delegate.findByIdAndUserId(id, userId);
                readsReached.countDown();
                awaitPeer(readsReached);
                return engagement;
            }
        };
    }

    private ResearchEvidenceRepository gateEvidenceCounts(ResearchEvidenceRepository delegate,
                                                            CountDownLatch countsReached) {
        return new ResearchEvidenceRepository() {
            @Override public ResearchEvidence save(ResearchEvidence evidence) { return delegate.save(evidence); }
            @Override public List<ResearchEvidence> findByEngagementId(UUID engagementId) {
                return delegate.findByEngagementId(engagementId);
            }
            @Override public long countByEngagementId(UUID engagementId) {
                long count = delegate.countByEngagementId(engagementId);
                countsReached.countDown();
                awaitPeer(countsReached);
                return count;
            }
            @Override public List<ResearchEvidence> findByIdInAndEngagementId(List<UUID> ids, UUID engagementId) {
                return delegate.findByIdInAndEngagementId(ids, engagementId);
            }
            @Override public Map<UUID, Long> countByEngagementIds(List<UUID> ids) {
                return delegate.countByEngagementIds(ids);
            }
        };
    }

    private List<Outcome> runConcurrently(Runnable firstCommand, Runnable secondCommand) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Outcome> first = executor.submit(() -> execute(start, firstCommand));
            Future<Outcome> second = executor.submit(() -> execute(start, secondCommand));
            start.countDown();
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
    }

    private Outcome execute(CountDownLatch start, Runnable command) throws Exception {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            inTransaction(() -> { command.run(); return null; });
            return new Outcome(null);
        } catch (RuntimeException exception) {
            return new Outcome(exception);
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

    private TestData persistData(boolean selectLead) {
        User user = User.create(UUID.randomUUID() + "@example.com", "hash", "Learner", UserRole.LEARNER);
        Scenario scenario = Scenario.create("Lead scenario", "Technology", "Scenario", 3);
        Persona persona = Persona.create(scenario, "Client", "CIO", "Example Co", "Direct", "Risk",
                "Budget", "Delivery");
        Lead first = Lead.create(scenario.getId(), "First Co", "Technology", "Modernisation", LeadDifficulty.MEDIUM);
        Lead second = Lead.create(scenario.getId(), "Second Co", "Technology", "Modernisation", LeadDifficulty.MEDIUM);
        entityManager.persist(user);
        entityManager.persist(scenario);
        entityManager.persist(persona);
        entityManager.persist(first);
        entityManager.persist(second);
        Engagement engagement = Engagement.start(user.getId(), scenario.getId(), persona.getId());
        if (selectLead) {
            engagement.selectLead(first.getId());
        }
        entityManager.persist(engagement);
        entityManager.flush();
        return new TestData(user.getId(), engagement.getId(), first.getId(), second.getId());
    }

    private <T> T inTransaction(Callable<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try { return work.call(); }
            catch (RuntimeException exception) { throw exception; }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        });
    }

    private class EntityManagerEngagementRepository implements EngagementRepository {
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

    private record TestData(UUID userId, UUID engagementId, UUID firstLeadId, UUID secondLeadId) {}
    private record Outcome(RuntimeException failure) {
        boolean succeeded() { return failure == null; }
    }
}
