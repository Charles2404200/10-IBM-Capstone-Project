package com.ibm.consulting.sim.scenario.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ibm.consulting.sim.knowledge.application.KnowledgeIngestionService;
import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadRepository;
import com.ibm.consulting.sim.scenario.domain.CanonicalFact;
import com.ibm.consulting.sim.scenario.domain.RevealRule;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioAuthoringConfig;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.scenario.domain.ScenarioStatus;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditAction;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditLogger;

@ExtendWith(MockitoExtension.class)
class ScenarioServiceTest {
    @Mock ScenarioRepository scenarioRepository;
    @Mock DifficultyProfileService difficultyProfileService;
    @Mock ScenarioAuthoringConfigService authoringConfigService;
    @Mock LeadRepository leadRepository;
    @Mock KnowledgeIngestionService knowledgeIngestionService;
    @Mock AuditLogger auditLogger;

    @InjectMocks ScenarioService service;

    private void mockDifficultyProfile() {
        when(difficultyProfileService.forScenario(any(Scenario.class)))
            .thenAnswer(invocation -> {
                Scenario scenario = invocation.getArgument(0);
                return com.ibm.consulting.sim.scenario.domain.DifficultyProfile.defaults(
                    scenario.getDifficulty(),
                    scenario.getInformationAmbiguity(),
                    scenario.getStakeholderComplexity(),
                    scenario.getCommercialPressure());
            });
    }

    private void mockScenarioReadiness(Scenario scenario) {
        // mock values needed for publish readiness
        ScenarioAuthoringConfig config = mock(ScenarioAuthoringConfig.class);
        when(config.canonicalFacts()).thenReturn(List.of(mock(CanonicalFact.class)));
        when(config.revealRules()).thenReturn(List.of(mock(RevealRule.class)));
        when(authoringConfigService.forScenario(scenario)).thenReturn(config);

        Lead lead = mock(Lead.class);
        when(leadRepository.findByScenarioId(scenario.getId())).thenReturn(List.of(lead));
    }

    // audit logging - publish scenario
    @Test
    void logsScenarioPublished() {
        // create scenario
        Scenario scenario = Scenario.create("Test Scenario", "Retail", "Test description", 3);
        scenario.updateBriefing("Consultant", "Test objective", List.of("Test success criteria"), 3);
        scenario.updateRubricWeights(Map.of("Communication", 50, "Commercial", 50));
        scenario.addPersona("Test Persona", "Manager", "Test Organisation", null, null, null, null);

        UUID scenarioId = scenario.getId();

        // mock scenario
        when(scenarioRepository.findById(scenarioId)).thenReturn(Optional.of(scenario));
        when(scenarioRepository.findByLineageIdAndStatus(any(), eq(ScenarioStatus.ACTIVE))).thenReturn(List.of());
        when(scenarioRepository.save(scenario)).thenReturn(scenario);

        mockDifficultyProfile();
        mockScenarioReadiness(scenario);

        // publish scenario
        service.publish(scenarioId);
        verify(auditLogger).recordAdmin(
            eq(AuditAction.ADMIN_SCENARIO_PUBLISHED),
            eq("SCENARIO"),
            eq(scenarioId.toString()));
    }

    // audit logging - archive scenario
    @Test
    void logsScenarioArchived() {
        // create scenario
        Scenario scenario = Scenario.create("Test Scenario", "Retail", "Test description", 3);
        UUID scenarioId = scenario.getId();

        // mock scenario
        when(scenarioRepository.findById(scenarioId)).thenReturn(Optional.of(scenario));
        when(scenarioRepository.save(scenario)).thenReturn(scenario);

        mockDifficultyProfile();

        // archive scenario
        service.archive(scenarioId);
        verify(auditLogger).recordAdmin(
            eq(AuditAction.ADMIN_SCENARIO_ARCHIVED),
            eq("SCENARIO"),
            eq(scenarioId.toString()));
    }

    // audit logging - change rubric weights
    @Test
    void logsRubricChanged() {
        Map<String, Integer> weights = Map.of("Communication", 50, "Commercial", 50);

        // create scenario
        Scenario scenario = Scenario.create("Test Scenario", "Retail", "Test description", 3);
        UUID scenarioId = scenario.getId();

        // mock scenario
        when(scenarioRepository.findById(scenarioId)).thenReturn(Optional.of(scenario));
        when(scenarioRepository.save(scenario)).thenReturn(scenario);

        mockDifficultyProfile();

        // update rubric weights
        service.updateRubricWeights(scenarioId, weights);
        verify(auditLogger).recordAdmin(
            eq(AuditAction.ADMIN_SCENARIO_RUBRIC_CHANGED),
            eq("SCENARIO"),
            eq(scenarioId.toString()),
            eq(weights.toString()));
    }

    // audit logging - delete lead
    @Test
    void logsLeadDeleted() {
        UUID scenarioId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();

        // mock scenario
        Scenario scenario = mock(Scenario.class);
        when(scenario.getStatus()).thenReturn(ScenarioStatus.DRAFT);
        when(scenarioRepository.findById(scenarioId)).thenReturn(Optional.of(scenario));

        // mock existing lead
        Lead lead = mock(Lead.class);
        when(lead.getScenarioId()).thenReturn(scenarioId);
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));

        // delete lead
        service.deleteLead(scenarioId, leadId);
        verify(auditLogger).recordAdmin(
            eq(AuditAction.ADMIN_SCENARIO_LEAD_DELETED),
            eq("LEAD"),
            eq(leadId.toString()),
            contains("scenario " + scenarioId));
    }
}