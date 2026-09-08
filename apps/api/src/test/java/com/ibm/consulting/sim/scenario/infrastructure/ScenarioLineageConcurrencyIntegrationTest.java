package com.ibm.consulting.sim.scenario.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.knowledge.application.KnowledgeIngestionService;
import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadCatalogPage;
import com.ibm.consulting.sim.lead.domain.LeadCatalogQuery;
import com.ibm.consulting.sim.lead.domain.LeadDifficulty;
import com.ibm.consulting.sim.lead.domain.LeadRepository;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.lead.application.LeadService;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.application.ScenarioAuthoringConfigService;
import com.ibm.consulting.sim.scenario.application.ScenarioAuthoringView;
import com.ibm.consulting.sim.scenario.application.ScenarioService;
import com.ibm.consulting.sim.scenario.domain.CanonicalFact;
import com.ibm.consulting.sim.scenario.domain.RevealRule;
import com.ibm.consulting.sim.scenario.domain.RevealTarget;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioAuthoringConfig;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.scenario.domain.ScenarioStatus;
import com.ibm.consulting.sim.lead.domain.EvidenceType;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditLogger;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest
@Import(JpaScenarioRepository.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScenarioLineageConcurrencyIntegrationTest {

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
    @Autowired ScenarioRepository scenarioRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void concurrentRevisionAndPublicationPreserveLineageInvariantsAndCopiedContent() throws Exception {
        UUID sourceId = inTransaction(() -> persistReadyScenario("Primary lineage", true));
        KnowledgeIngestionService knowledge = mock(KnowledgeIngestionService.class);
        ScenarioService service = service(knowledge);

        List<ScenarioAuthoringView> revisions = runConcurrently(
                () -> service.createRevision(sourceId),
                () -> service.createRevision(sourceId));
        List<UUID> revisionIds = revisions.stream().map(view -> view.scenario().id()).toList();

        inTransaction(() -> {
            List<Scenario> afterRevision = scenarioRepository.findLineageForUpdate(sourceId);
            assertThat(afterRevision).extracting(Scenario::getContentVersion).containsExactly(1, 2, 3);
            assertThat(afterRevision).filteredOn(scenario -> !scenario.getId().equals(sourceId))
                    .allSatisfy(scenario -> assertThat(scenario.getPersonas()).hasSize(1));
            return null;
        });
        for (UUID revisionId : revisionIds) {
            assertThat(inTransaction(() -> new EntityManagerLeadRepository().findByScenarioId(revisionId))).hasSize(1);
            verify(knowledge).copyScenarioDocuments(eq(sourceId), eq(revisionId), any());
        }

        runConcurrently(
                () -> service.publish(revisionIds.get(0)),
                () -> service.publish(revisionIds.get(1)));

        List<Scenario> afterPublication = inTransaction(() -> scenarioRepository.findLineageForUpdate(sourceId));
        assertThat(afterPublication).filteredOn(scenario -> scenario.getStatus() == ScenarioStatus.ACTIVE).hasSize(1);

        UUID unrelatedId = inTransaction(() -> persistReadyScenario("Unrelated lineage", false));
        inTransaction(() -> service.publish(unrelatedId));
        assertThat(inTransaction(() -> scenarioRepository.findById(unrelatedId)).orElseThrow().getStatus())
                .isEqualTo(ScenarioStatus.ACTIVE);
        verify(knowledge, times(2)).copyScenarioDocuments(eq(sourceId), any(), any());
    }

    @Test
    void repeatedRevisionFromTheSameOlderSourceAllocatesTheNextLineageVersion() {
        UUID sourceId = inTransaction(() -> persistReadyScenario("Repeated source", true));
        ScenarioService service = service(mock(KnowledgeIngestionService.class));

        ScenarioAuthoringView second = inTransaction(() -> service.createRevision(sourceId));
        ScenarioAuthoringView third = inTransaction(() -> service.createRevision(sourceId));

        assertThat(second.scenario().contentVersion()).isEqualTo(2);
        assertThat(third.scenario().contentVersion()).isEqualTo(3);
    }

    @Test
    void learnerScenarioAndLeadLookupsExcludeDraftArchivedAndMissingScenarios() {
        UUID activeId = inTransaction(() -> persistReadyScenario("Active", true));
        UUID draftId = inTransaction(() -> persistReadyScenario("Draft", false));
        UUID archivedId = inTransaction(() -> {
            UUID id = persistReadyScenario("Archived", false);
            scenarioRepository.findById(id).orElseThrow().archive();
            return id;
        });
        ScenarioService scenarios = service(mock(KnowledgeIngestionService.class));
        LeadService leads = new LeadService(
                new EntityManagerLeadRepository(), mock(ResearchEvidenceRepository.class),
                mock(EngagementRepository.class), new DifficultyProfileService(new ObjectMapper(), scenarioRepository),
                scenarioRepository, new ScenarioAuthoringConfigService(new ObjectMapper()));

        assertThat(inTransaction(() -> scenarios.getActiveById(activeId)).id()).isEqualTo(activeId);
        assertThat(inTransaction(() -> leads.listForScenario(activeId))).hasSize(1);
        assertThatThrownBy(() -> inTransaction(() -> scenarios.getActiveById(draftId)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> inTransaction(() -> scenarios.getActiveById(archivedId)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> inTransaction(() -> leads.listForScenario(draftId)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> inTransaction(() -> leads.listForScenario(archivedId)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> inTransaction(() -> leads.listForScenario(UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class);

        // The authoring path intentionally uses unrestricted scenario lookup.
        assertThat(inTransaction(() -> scenarios.listAuthoringLeads(draftId))).hasSize(1);
    }

    private ScenarioService service(KnowledgeIngestionService knowledge) {
        ObjectMapper objectMapper = new ObjectMapper();
        return new ScenarioService(
                scenarioRepository,
                new DifficultyProfileService(objectMapper, scenarioRepository),
                new ScenarioAuthoringConfigService(objectMapper),
                new EntityManagerLeadRepository(),
                knowledge,
                mock(AuditLogger.class));
    }

    private UUID persistReadyScenario(String title, boolean active) {
        Scenario scenario = Scenario.create(title, "Technology", "A production-ready scenario", 3);
        scenario.updateBriefing("Consultant", "Deliver a grounded recommendation", List.of("Evidence used"), 10);
        scenario.updateRubricWeights(java.util.Map.of("Communication", 100));
        scenario.addPersona("Client", "CIO", "Example Corp", "Direct", "Delivery risk", "Budget", "Modernise");
        new ScenarioAuthoringConfigService(new ObjectMapper()).update(scenario, new ScenarioAuthoringConfig(
                List.of(new CanonicalFact("fact-1", "Constraint", "Budget is capped", EvidenceType.FINANCIAL_SIGNAL, true)),
                List.of(new RevealRule(RevealTarget.BUDGET_SIGNAL, Set.of(EvidenceType.FINANCIAL_SIGNAL), 1))));
        if (active) scenario.publish();
        entityManager.persist(scenario);
        entityManager.persist(Lead.create(scenario.getId(), "Example Corp", "Technology", "Modernisation opportunity", LeadDifficulty.MEDIUM));
        entityManager.flush();
        return scenario.getId();
    }

    private <T> List<T> runConcurrently(java.util.concurrent.Callable<T> firstCommand,
                                        java.util.concurrent.Callable<T> secondCommand) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<T> first = executor.submit(() -> execute(start, firstCommand));
            Future<T> second = executor.submit(() -> execute(start, secondCommand));
            start.countDown();
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
    }

    private <T> T execute(CountDownLatch start, java.util.concurrent.Callable<T> command) throws Exception {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return inTransaction(command);
    }

    private <T> T inTransaction(java.util.concurrent.Callable<T> work) {
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

    private final class EntityManagerLeadRepository implements LeadRepository {
        @Override public List<Lead> findByScenarioId(UUID scenarioId) {
            return entityManager.createQuery("select lead from Lead lead where lead.scenarioId = :scenarioId", Lead.class)
                    .setParameter("scenarioId", scenarioId)
                    .getResultList();
        }
        @Override public LeadCatalogPage findCatalog(LeadCatalogQuery query) { throw new UnsupportedOperationException(); }
        @Override public List<String> findCatalogIndustries() { return List.of(); }
        @Override public Optional<Lead> findById(UUID id) { return Optional.ofNullable(entityManager.find(Lead.class, id)); }
        @Override public List<Lead> findByIdIn(List<UUID> ids) { return List.of(); }
        @Override public Lead save(Lead lead) { entityManager.persist(lead); return lead; }
        @Override public void delete(Lead lead) { entityManager.remove(lead); }
    }
}
