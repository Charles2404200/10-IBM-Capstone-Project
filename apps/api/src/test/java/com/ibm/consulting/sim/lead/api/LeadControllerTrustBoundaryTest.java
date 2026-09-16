package com.ibm.consulting.sim.lead.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.lead.application.LeadNotInScenarioException;
import com.ibm.consulting.sim.lead.application.LeadService;
import com.ibm.consulting.sim.lead.application.ResearchIntelligenceService;
import com.ibm.consulting.sim.lead.application.ResearchEvidenceSummary;
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
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeadControllerTrustBoundaryTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void writableResearchSchemaDoesNotAdvertiseServerOwnedProvenance() throws Exception {
        assertThat(Stream.of(LeadController.SaveResearchRequest.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .doesNotContain("origin", "verificationStatus");

        var request = new LeadController.SaveResearchRequest(
                "Learner note", null, EvidenceType.COMPANY_NEWS, null, null,
                null, ConfidenceLevel.HIGH, 100, null, Set.of());
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(request));
        assertThat(json.has("origin")).isFalse();
        assertThat(json.has("verificationStatus")).isFalse();
    }

    @Test
    void learnerResearchContractAssignsServerOwnedProvenance() {
        LeadService leadService = mock(LeadService.class);
        LeadController controller = new LeadController(leadService, mock(ResearchIntelligenceService.class));
        User learner = mock(User.class);
        UUID userId = UUID.randomUUID();
        UUID engagementId = UUID.randomUUID();
        when(learner.getId()).thenReturn(userId);

        controller.saveResearch(engagementId, new LeadController.SaveResearchRequest(
                "Learner note", null, EvidenceType.COMPANY_NEWS, null, null,
                null, ConfidenceLevel.HIGH, 100, null, Set.of()), learner);

        verify(leadService).saveEvidence(eq(engagementId), eq(userId), eq("Learner note"), any(),
                eq(EvidenceType.COMPANY_NEWS), any(), any(), eq(EvidenceOrigin.USER_SUPPLIED),
                eq(EvidenceVerificationStatus.UNVERIFIED), any(), eq(ConfidenceLevel.HIGH), eq(100), isNull(),
                eq(Set.of()));
    }

    @Test
    void trustedInternalCreationStillSupportsCuratedVerifiedEvidence() {
        ResearchEvidence evidence = ResearchEvidence.builder()
                .engagementId(UUID.randomUUID())
                .leadId(UUID.randomUUID())
                .note("Scenario-authored client fact")
                .evidenceType(EvidenceType.STAKEHOLDER_PROFILE)
                .origin(EvidenceOrigin.SCENARIO_CURATED)
                .verificationStatus(EvidenceVerificationStatus.VERIFIED)
                .sequenceNo(1)
                .build();

        assertThat(evidence.getOrigin()).isEqualTo(EvidenceOrigin.SCENARIO_CURATED);
        assertThat(evidence.getVerificationStatus()).isEqualTo(EvidenceVerificationStatus.VERIFIED);
    }

    @Test
    void researchResponseSerializesServerOwnedProvenance() throws Exception {
        ResearchEvidence evidence = ResearchEvidence.builder()
                .engagementId(UUID.randomUUID())
                .leadId(UUID.randomUUID())
                .note("Learner evidence")
                .evidenceType(EvidenceType.COMPANY_NEWS)
                .sequenceNo(1)
                .build();

        JsonNode json = objectMapper.readTree(
                objectMapper.writeValueAsBytes(ResearchEvidenceSummary.from(evidence)));

        assertThat(json.path("origin").asText()).isEqualTo("USER_SUPPLIED");
        assertThat(json.path("verificationStatus").asText()).isEqualTo("UNVERIFIED");
    }

    @Test
    void crossScenarioLeadSelectionLeavesTheEngagementUntouched() {
        UUID userId = UUID.randomUUID();
        UUID engagementId = UUID.randomUUID();
        UUID scenarioA = UUID.randomUUID();
        UUID scenarioB = UUID.randomUUID();
        Engagement engagement = Engagement.start(userId, scenarioA, UUID.randomUUID(), "frozen-profile");
        Lead foreignLead = Lead.create(scenarioB, "Foreign Co", "Finance",
                "Unrelated opportunity", LeadDifficulty.HARD);
        EngagementRepository engagements = mock(EngagementRepository.class);
        LeadRepository leads = mock(LeadRepository.class);
        when(engagements.findByIdAndUserIdForUpdate(engagementId, userId))
                .thenReturn(Optional.of(engagement));
        when(leads.findById(foreignLead.getId())).thenReturn(Optional.of(foreignLead));
        LeadService service = service(leads, engagements);

        assertThatThrownBy(() -> service.selectLead(engagementId, foreignLead.getId(), userId))
                .isInstanceOf(LeadNotInScenarioException.class);

        assertThat(engagement.getSelectedLeadId()).isNull();
        assertThat(engagement.getState()).isEqualTo(EngagementState.QUALIFYING);
        assertThat(engagement.getDifficultyProfileSnapshot()).isEqualTo("frozen-profile");
        assertThat(engagement.getEvents()).hasSize(1);
        verify(engagements, never()).save(any());
    }

    @Test
    void reselectingTheSameLeadIsAnIdempotentNoOp() {
        UUID userId = UUID.randomUUID();
        UUID engagementId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        Engagement engagement = Engagement.start(userId, UUID.randomUUID(), UUID.randomUUID());
        engagement.selectLead(leadId, "lead-profile");
        EngagementRepository engagements = mock(EngagementRepository.class);
        LeadRepository leads = mock(LeadRepository.class);
        when(engagements.findByIdAndUserIdForUpdate(engagementId, userId))
                .thenReturn(Optional.of(engagement));
        LeadService service = service(leads, engagements);

        service.selectLead(engagementId, leadId, userId);

        assertThat(engagement.getSelectedLeadId()).isEqualTo(leadId);
        assertThat(engagement.getState()).isEqualTo(EngagementState.CLIENT_INTELLIGENCE);
        assertThat(engagement.getEvents()).hasSize(2);
        verifyNoInteractions(leads);
        verify(engagements, never()).save(any());
    }

    private LeadService service(LeadRepository leads, EngagementRepository engagements) {
        return new LeadService(leads, mock(ResearchEvidenceRepository.class), engagements,
                mock(DifficultyProfileService.class), mock(ScenarioRepository.class),
                mock(ScenarioAuthoringConfigService.class));
    }
}
