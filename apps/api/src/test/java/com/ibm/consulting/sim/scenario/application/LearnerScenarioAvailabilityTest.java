package com.ibm.consulting.sim.scenario.application;

import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.knowledge.application.KnowledgeIngestionService;
import com.ibm.consulting.sim.lead.application.LeadService;
import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadDifficulty;
import com.ibm.consulting.sim.lead.domain.LeadRepository;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.scenario.domain.ScenarioStatus;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditLogger;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearnerScenarioAvailabilityTest {

    @Test
    void learnerLookupsRequireActiveScenarioWhileAuthoringRetainsDraftAccess() {
        ScenarioRepository scenarios = mock(ScenarioRepository.class);
        LeadRepository leads = mock(LeadRepository.class);
        DifficultyProfileService difficulty = mock(DifficultyProfileService.class);
        ScenarioAuthoringConfigService authoring = mock(ScenarioAuthoringConfigService.class);
        ScenarioService scenarioService = new ScenarioService(
                scenarios, difficulty, authoring, leads, mock(KnowledgeIngestionService.class), mock(AuditLogger.class));
        LeadService leadService = new LeadService(
                leads, mock(ResearchEvidenceRepository.class), mock(EngagementRepository.class), difficulty,
                scenarios, authoring);

        Scenario active = Scenario.create("Active", "Retail", "Available", 3);
        active.publish();
        Scenario draft = Scenario.create("Draft", "Retail", "Unpublished", 3);
        Scenario archived = Scenario.create("Archived", "Retail", "Retired", 3);
        archived.archive();
        Lead activeLead = Lead.create(active.getId(), "Active client", "Retail", "Available", LeadDifficulty.MEDIUM);
        Lead draftLead = Lead.create(draft.getId(), "Draft client", "Retail", "Hidden", LeadDifficulty.MEDIUM);
        when(difficulty.forScenario(active)).thenReturn(DifficultyProfile.defaults(3, 3, 3, 3));
        when(scenarios.findByIdAndStatus(active.getId(), ScenarioStatus.ACTIVE)).thenReturn(Optional.of(active));
        when(scenarios.findByIdAndStatus(draft.getId(), ScenarioStatus.ACTIVE)).thenReturn(Optional.empty());
        when(scenarios.findByIdAndStatus(archived.getId(), ScenarioStatus.ACTIVE)).thenReturn(Optional.empty());
        when(scenarios.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(leads.findByScenarioId(active.getId())).thenReturn(List.of(activeLead));
        when(leads.findByScenarioId(draft.getId())).thenReturn(List.of(draftLead));

        assertThat(scenarioService.getActiveById(active.getId()).id()).isEqualTo(active.getId());
        assertThat(leadService.listForScenario(active.getId())).hasSize(1);
        assertThatThrownBy(() -> scenarioService.getActiveById(draft.getId())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> scenarioService.getActiveById(archived.getId())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> leadService.listForScenario(draft.getId())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> leadService.listForScenario(archived.getId())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> leadService.listForScenario(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        assertThat(scenarioService.listAuthoringLeads(draft.getId())).hasSize(1);
    }
}
