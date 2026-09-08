package com.ibm.consulting.sim.lead.api;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.lead.application.LeadService;
import com.ibm.consulting.sim.lead.application.ResearchIntelligenceService;
import com.ibm.consulting.sim.lead.domain.ConfidenceLevel;
import com.ibm.consulting.sim.lead.domain.EvidenceOrigin;
import com.ibm.consulting.sim.lead.domain.EvidenceType;
import com.ibm.consulting.sim.lead.domain.EvidenceVerificationStatus;
import com.ibm.consulting.sim.lead.domain.ResearchEvidence;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class LeadControllerTrustBoundaryTest {

    @Test
    void learnerSuppliedProvenanceCannotClaimCuratedOrCorroboratedTrust() {
        LeadService leadService = mock(LeadService.class);
        LeadController controller = new LeadController(leadService, mock(ResearchIntelligenceService.class));
        User learner = mock(User.class);
        UUID userId = UUID.randomUUID();
        UUID engagementId = UUID.randomUUID();
        when(learner.getId()).thenReturn(userId);

        controller.saveResearch(engagementId, new LeadController.SaveResearchRequest(
                "Learner note", null, EvidenceType.COMPANY_NEWS, null, null,
                EvidenceOrigin.SCENARIO_CURATED, EvidenceVerificationStatus.CORROBORATED,
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
}
