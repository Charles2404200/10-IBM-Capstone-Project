package com.ibm.consulting.sim.lead.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.lead.application.LeadService;
import com.ibm.consulting.sim.lead.application.ResearchIntelligenceService;
import com.ibm.consulting.sim.lead.application.ResearchEvidenceSummary;
import com.ibm.consulting.sim.lead.domain.ConfidenceLevel;
import com.ibm.consulting.sim.lead.domain.EvidenceOrigin;
import com.ibm.consulting.sim.lead.domain.EvidenceType;
import com.ibm.consulting.sim.lead.domain.EvidenceVerificationStatus;
import com.ibm.consulting.sim.lead.domain.ResearchEvidence;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

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
                null, ConfidenceLevel.HIGH, 100, Set.of());
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
                null, ConfidenceLevel.HIGH, 100, Set.of()), learner);

        verify(leadService).saveEvidence(eq(engagementId), eq(userId), eq("Learner note"), any(),
                eq(EvidenceType.COMPANY_NEWS), any(), any(), eq(EvidenceOrigin.USER_SUPPLIED),
                eq(EvidenceVerificationStatus.UNVERIFIED), any(), eq(ConfidenceLevel.HIGH), eq(100), eq(Set.of()));
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
}
