package com.ibm.consulting.sim.knowledge.application;

import com.ibm.consulting.sim.ai.domain.EmbeddingGateway;
import com.ibm.consulting.sim.knowledge.domain.DocumentChunk;
import com.ibm.consulting.sim.knowledge.domain.DocumentChunkRepository;
import com.ibm.consulting.sim.knowledge.domain.KnowledgeCollection;
import com.ibm.consulting.sim.knowledge.domain.KnowledgeDocument;
import com.ibm.consulting.sim.knowledge.domain.KnowledgeDocumentRepository;
import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadRepository;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.application.ScenarioAuthoringConfigService;
import com.ibm.consulting.sim.scenario.application.ScenarioService;
import com.ibm.consulting.sim.scenario.domain.CanonicalFact;
import com.ibm.consulting.sim.scenario.domain.PersonaRepository;
import com.ibm.consulting.sim.scenario.domain.RevealRule;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioAuthoringConfig;
import com.ibm.consulting.sim.scenario.domain.ScenarioCatalogPage;
import com.ibm.consulting.sim.scenario.domain.ScenarioCatalogQuery;
import com.ibm.consulting.sim.scenario.domain.AdminScenarioCatalogQuery;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.scenario.domain.ScenarioStatus;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditLogger;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class KnowledgePublicationConcurrencyIntegrationTest {

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

    @ParameterizedTest
    @EnumSource(Mutation.class)
    void publicationSerializesAgainstEveryKnowledgeMutation(Mutation mutation) throws Exception {
        Fixture fixture = inTransaction(() -> persistReadyScenario(mutation));
        ScenarioRepository scenarios = new EntityManagerScenarioRepository();
        CountDownLatch publicationHasLock = new CountDownLatch(1);
        CountDownLatch mutationStarted = new CountDownLatch(1);
        ScenarioAuthoringConfigService config = mock(ScenarioAuthoringConfigService.class);
        ScenarioAuthoringConfig readyConfig = mock(ScenarioAuthoringConfig.class);
        when(readyConfig.canonicalFacts()).thenReturn(List.of(mock(CanonicalFact.class)));
        when(readyConfig.revealRules()).thenReturn(List.of(mock(RevealRule.class)));
        when(config.forScenario(any(Scenario.class))).thenAnswer(invocation -> {
            publicationHasLock.countDown();
            assertThat(mutationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            return readyConfig;
        });
        LeadRepository leads = mock(LeadRepository.class);
        when(leads.findByScenarioId(fixture.scenarioId())).thenReturn(List.of(mock(Lead.class)));
        DifficultyProfileService difficulty = mock(DifficultyProfileService.class);
        when(difficulty.forScenario(any(Scenario.class)))
                .thenReturn(com.ibm.consulting.sim.scenario.domain.DifficultyProfile.defaults(3, 3, 3, 3));
        ScenarioService scenarioService = new ScenarioService(scenarios, difficulty, config, leads,
                mock(KnowledgeIngestionService.class), mock(AuditLogger.class));
        EmbeddingGateway embeddings = mock(EmbeddingGateway.class);
        KnowledgeIngestionService knowledgeService = new KnowledgeIngestionService(
                new EntityManagerKnowledgeDocumentRepository(), new NoOpChunkRepository(), embeddings,
                mock(PersonaRepository.class), scenarios, mock(AuditLogger.class));

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> publication = executor.submit(() -> inTransaction(() -> scenarioService.publish(fixture.scenarioId())));
            assertThat(publicationHasLock.await(5, TimeUnit.SECONDS)).isTrue();
            Future<RuntimeException> knowledgeMutation = executor.submit(() -> {
                mutationStarted.countDown();
                try {
                    inTransaction(() -> {
                        executeMutation(mutation, knowledgeService, fixture);
                        return null;
                    });
                    return null;
                } catch (RuntimeException failure) {
                    return failure;
                }
            });

            publication.get(15, TimeUnit.SECONDS);
            assertThat(knowledgeMutation.get(15, TimeUnit.SECONDS))
                    .isInstanceOf(KnowledgeIngestionService.ScenarioContentLockedException.class);
        }

        Scenario published = inTransaction(() -> entityManager.find(Scenario.class, fixture.scenarioId()));
        assertThat(published.getStatus()).isEqualTo(ScenarioStatus.ACTIVE);
        verify(embeddings, never()).embed(any());
        assertThat(documentCount(fixture.scenarioId())).isEqualTo(mutation == Mutation.UPLOAD ? 0L : 1L);
    }

    private void executeMutation(Mutation mutation, KnowledgeIngestionService service, Fixture fixture) {
        switch (mutation) {
            case UPLOAD -> service.ingest(fixture.scenarioId(), null, KnowledgeCollection.SCENARIO_TRUTH,
                    "New", "New content");
            case UPDATE -> service.update(fixture.scenarioId(), fixture.documentId(), null,
                    KnowledgeCollection.SCENARIO_TRUTH, "Updated", "Updated content");
            case DELETE -> service.delete(fixture.scenarioId(), fixture.documentId());
        }
    }

    private Fixture persistReadyScenario(Mutation mutation) {
        Scenario scenario = Scenario.create("Ready", "Technology", "Description", 3);
        scenario.updateBriefing("Consultant", "Objective", List.of("Success"), 5);
        scenario.updateRubricWeights(Map.of("Communication", 100));
        scenario.addPersona("Client", "CIO", "Example", "Direct", null, null, null);
        entityManager.persist(scenario);
        UUID documentId = null;
        if (mutation != Mutation.UPLOAD) {
            KnowledgeDocument document = KnowledgeDocument.create(scenario.getId(), null,
                    KnowledgeCollection.SCENARIO_TRUTH, "Existing", "Existing content");
            entityManager.persist(document);
            documentId = document.getId();
        }
        entityManager.flush();
        return new Fixture(scenario.getId(), documentId);
    }

    private Long documentCount(UUID scenarioId) {
        return inTransaction(() -> entityManager.createQuery(
                        "select count(document) from KnowledgeDocument document where document.scenarioId = :id",
                        Long.class).setParameter("id", scenarioId).getSingleResult());
    }

    private <T> T inTransaction(java.util.concurrent.Callable<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try { return work.call(); }
            catch (RuntimeException exception) { throw exception; }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        });
    }

    private final class EntityManagerScenarioRepository implements ScenarioRepository {
        @Override public List<Scenario> findAllActive() { return List.of(); }
        @Override public ScenarioCatalogPage findCatalog(ScenarioCatalogQuery query) { throw new UnsupportedOperationException(); }
        @Override public ScenarioCatalogPage findAdminCatalog(AdminScenarioCatalogQuery query) { throw new UnsupportedOperationException(); }
        @Override public List<String> findCatalogIndustries() { return List.of(); }
        @Override public List<Scenario> findAll() { return List.of(); }
        @Override public List<Scenario> findByLineageIdAndStatus(UUID lineageId, ScenarioStatus status) { return List.of(); }
        @Override public Optional<Scenario> findById(UUID id) {
            return Optional.ofNullable(entityManager.find(Scenario.class, id));
        }
        @Override public Optional<Scenario> findByIdForUpdate(UUID id) {
            return Optional.ofNullable(entityManager.find(Scenario.class, id, LockModeType.PESSIMISTIC_WRITE));
        }
        @Override public List<Scenario> findByIdIn(List<UUID> ids) { return List.of(); }
        @Override public Scenario save(Scenario scenario) { return scenario; }
    }

    private final class EntityManagerKnowledgeDocumentRepository implements KnowledgeDocumentRepository {
        @Override public KnowledgeDocument save(KnowledgeDocument document) { return entityManager.merge(document); }
        @Override public List<KnowledgeDocument> findByScenarioId(UUID scenarioId) { return List.of(); }
        @Override public Optional<KnowledgeDocument> findById(UUID id) {
            return Optional.ofNullable(entityManager.find(KnowledgeDocument.class, id));
        }
        @Override public void deleteById(UUID id) {
            KnowledgeDocument document = entityManager.find(KnowledgeDocument.class, id);
            if (document != null) entityManager.remove(document);
        }
    }

    private static final class NoOpChunkRepository implements DocumentChunkRepository {
        @Override public DocumentChunk save(DocumentChunk chunk) { return chunk; }
        @Override public List<DocumentChunk> saveAll(List<DocumentChunk> chunks) { return chunks; }
        @Override public List<DocumentChunk> findByCollectionAndScope(
                KnowledgeCollection collection, UUID scenarioId, UUID personaId) { return List.of(); }
        @Override public void deleteByDocumentId(UUID documentId) {}
    }

    private enum Mutation { UPLOAD, UPDATE, DELETE }
    private record Fixture(UUID scenarioId, UUID documentId) {}
}
