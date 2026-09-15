package com.ibm.consulting.sim.scenario.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.engagement.application.EngagementLifecycleCoordinator;
import com.ibm.consulting.sim.engagement.application.StartEngagementUseCase;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.engagement.domain.LifecycleGateNotSatisfiedException;
import com.ibm.consulting.sim.engagement.infrastructure.JpaLifecycleFactRepository;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.lead.domain.ConfidenceLevel;
import com.ibm.consulting.sim.lead.domain.EvidenceType;
import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadDifficulty;
import com.ibm.consulting.sim.lead.domain.LeadRepository;
import com.ibm.consulting.sim.lead.domain.ResearchEvidence;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.application.LifecycleDefinitionCodec;
import com.ibm.consulting.sim.scenario.application.ScenarioLifecycleService;
import com.ibm.consulting.sim.scenario.application.UpdateScenarioLifecycleRequest;
import com.ibm.consulting.sim.scenario.domain.LifecycleConditionNode;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioLifecycleDefinition;
import com.ibm.consulting.sim.scenario.domain.ScenarioObjectiveDefinition;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.scenario.domain.ScenarioStageDefinition;
import com.ibm.consulting.sim.scenario.domain.StageCapability;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditLogger;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import java.util.concurrent.atomic.AtomicReference;

import static com.ibm.consulting.sim.scenario.domain.LifecycleConditionType.MIN_EVIDENCE_COUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DataJpaTest
@Import({JpaScenarioRepository.class, JpaLifecycleFactRepository.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConfigurableLifecycleEndToEndIntegrationTest {

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
    @Autowired ScenarioRepository scenarios;
    @Autowired JpaLifecycleFactRepository factRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void authoredLifecycleGatesLearnerProgressAndRunningEngagementKeepsItsSnapshot() {
        ObjectMapper objectMapper = new ObjectMapper();
        LifecycleDefinitionCodec codec = new LifecycleDefinitionCodec(objectMapper);
        ScenarioLifecycleService authoring = new ScenarioLifecycleService(scenarios, codec, mock(AuditLogger.class));
        EngagementLifecycleCoordinator lifecycle = new EngagementLifecycleCoordinator(
                codec, factRepository, new SimpleMeterRegistry());
        TestData data = inTransaction(this::persistDraftScenario);

        ScenarioLifecycleDefinition originalDefinition = configuredDefinition("Account discovery");
        inTransaction(() -> authoring.update(data.scenarioId(), new UpdateScenarioLifecycleRequest(
                originalDefinition, scenarios.findById(data.scenarioId()).orElseThrow().getVersion())));
        inTransaction(() -> {
            Scenario scenario = scenarios.findById(data.scenarioId()).orElseThrow();
            scenario.publish();
            scenarios.flush();
            return null;
        });

        EntityManagerEngagementRepository engagements = new EntityManagerEngagementRepository();
        var starter = new StartEngagementUseCase(engagements, scenarios,
                new DifficultyProfileService(objectMapper, scenarios), mock(LeadRepository.class), lifecycle);
        UUID userId = data.userId();
        UUID engagementId = inTransaction(() -> starter.execute(userId, data.scenarioId(), data.personaId()).id());

        inTransaction(() -> {
            Engagement engagement = engagements.findByIdAndUserIdForUpdate(engagementId, userId).orElseThrow();
            lifecycle.selectLead(engagement, data.leadId());
            return null;
        });
        assertThatThrownBy(() -> inTransaction(() -> {
            Engagement engagement = engagements.findByIdAndUserIdForUpdate(engagementId, userId).orElseThrow();
            lifecycle.transition(engagement, EngagementState.HYPOTHESIS_READY, "Research complete");
            return null;
        })).isInstanceOf(LifecycleGateNotSatisfiedException.class);

        inTransaction(() -> {
            entityManager.persist(ResearchEvidence.builder()
                    .engagementId(engagementId).leadId(data.leadId()).note("Verified transformation programme")
                    .evidenceType(EvidenceType.COMPANY_NEWS).confidence(ConfidenceLevel.HIGH).sequenceNo(1).build());
            return null;
        });
        inTransaction(() -> {
            Engagement engagement = engagements.findByIdAndUserIdForUpdate(engagementId, userId).orElseThrow();
            lifecycle.transition(engagement, EngagementState.HYPOTHESIS_READY, "Research complete");
            return null;
        });

        Scenario revision = inTransaction(() -> {
            Scenario active = scenarios.findById(data.scenarioId()).orElseThrow();
            Scenario draft = active.createRevision();
            scenarios.save(draft);
            scenarios.flush();
            return draft;
        });
        inTransaction(() -> authoring.update(revision.getId(), new UpdateScenarioLifecycleRequest(
                configuredDefinition("Revised discovery"), scenarios.findById(revision.getId()).orElseThrow().getVersion())));

        var running = inTransaction(() -> lifecycle.response(engagements.findById(engagementId).orElseThrow()));
        assertThat(running.state()).isEqualTo(EngagementState.HYPOTHESIS_READY);
        assertThat(running.lifecycle().stages()).filteredOn(stage -> stage.capability() == StageCapability.CLIENT_INTELLIGENCE)
                .extracting(stage -> stage.label()).containsExactly("Account discovery");
        assertThat(running.objectives()).extracting(objective -> objective.title())
                .containsExactly("Build an evidence base", "Confirm one credible signal");
        assertThat(codec.decode(inTransaction(() -> engagements.findById(engagementId).orElseThrow()
                .getLifecycleDefinitionSnapshot()))).isEqualTo(originalDefinition);
    }

    private TestData persistDraftScenario() {
        User user = User.create(UUID.randomUUID() + "@example.com", "hash", "Learner", UserRole.LEARNER);
        Scenario scenario = Scenario.create("Configurable lifecycle", "Technology", "Scenario", 3);
        var persona = scenario.addPersona("Client", "CIO", "Example Corp", "Direct", "Risk", "Budget", "Modernise");
        Lead lead = Lead.create(scenario.getId(), "Example Corp", "Technology", "Modernisation", LeadDifficulty.MEDIUM);
        entityManager.persist(user);
        entityManager.persist(scenario);
        entityManager.persist(lead);
        entityManager.flush();
        return new TestData(user.getId(), scenario.getId(), persona.getId(), lead.getId());
    }

    private ScenarioLifecycleDefinition configuredDefinition(String discoveryLabel) {
        ScenarioLifecycleDefinition defaults = ScenarioLifecycleDefinition.defaults();
        List<ScenarioStageDefinition> stages = defaults.stages().stream().map(stage ->
                stage.capability() == StageCapability.CLIENT_INTELLIGENCE
                        ? new ScenarioStageDefinition(stage.key(), stage.capability(), discoveryLabel,
                        stage.description(), stage.goal(), stage.doneText(), stage.nextText(), true,
                        stage.displayOrder(), stage.entryCondition(), LifecycleConditionNode.leaf(MIN_EVIDENCE_COUNT, 1))
                        : stage).toList();
        List<ScenarioObjectiveDefinition> objectives = List.of(
                new ScenarioObjectiveDefinition("EVIDENCE_BASE", null, "CLIENT_INTELLIGENCE",
                        "Build an evidence base", "Ground the recommendation in evidence", true, 0,
                        LifecycleConditionNode.leaf(MIN_EVIDENCE_COUNT, 1)),
                new ScenarioObjectiveDefinition("CREDIBLE_SIGNAL", "EVIDENCE_BASE", "CLIENT_INTELLIGENCE",
                        "Confirm one credible signal", "Find a defensible client signal", true, 1,
                        LifecycleConditionNode.leaf(MIN_EVIDENCE_COUNT, 1)));
        return new ScenarioLifecycleDefinition(1, stages, objectives);
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
        @Override public Engagement save(Engagement engagement) {
            if (entityManager.contains(engagement)) return engagement;
            entityManager.persist(engagement);
            return engagement;
        }
        @Override public List<Engagement> findAll() { return entityManager.createQuery("select e from Engagement e", Engagement.class).getResultList(); }
        @Override public Optional<Engagement> findById(UUID id) { return Optional.ofNullable(entityManager.find(Engagement.class, id)); }
        @Override public List<Engagement> findByUserId(UUID userId) { return List.of(); }
        @Override public List<Engagement> findDashboardByUserId(UUID userId) { return List.of(); }
        @Override public Optional<Engagement> findByIdAndUserId(UUID id, UUID userId) {
            return findById(id).filter(engagement -> userId.equals(engagement.getUserId()));
        }
        @Override public Optional<Engagement> findByIdAndUserIdForUpdate(UUID id, UUID userId) {
            return findByIdAndUserId(id, userId);
        }
    }

    private record TestData(UUID userId, UUID scenarioId, UUID personaId, UUID leadId) {}
}
