package com.ibm.consulting.sim.lead.application;

import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.lead.domain.ConfidenceLevel;
import com.ibm.consulting.sim.lead.domain.EvidenceOrigin;
import com.ibm.consulting.sim.lead.domain.EvidenceType;
import com.ibm.consulting.sim.lead.domain.EvidenceVerificationStatus;
import com.ibm.consulting.sim.lead.domain.LeadRepository;
import com.ibm.consulting.sim.lead.domain.ResearchEvidence;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.lead.domain.ResearchNotReadyException;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.application.ScenarioAuthoringConfigService;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeadResearchCompletionTest {

    @Test
    void insufficientResearchDoesNotAdvanceOrMutateEvidence() {
        Fixture fixture = fixture(List.of());

        assertThatThrownBy(() -> fixture.service().completeResearch(
                fixture.engagement().getId(), fixture.userId()))
                .isInstanceOf(ResearchNotReadyException.class);

        assertThat(fixture.engagement().getState()).isEqualTo(EngagementState.CLIENT_INTELLIGENCE);
        assertThat(fixture.engagement().getEvents()).hasSize(2);
        assertThat(fixture.engagement().getEvents())
                .noneMatch(event -> event.getState() == EngagementState.HYPOTHESIS_READY);
        verify(fixture.engagements(), never()).save(fixture.engagement());
        verify(fixture.evidence(), never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void readyResearchAdvancesExactlyOnceAndReplayIsSafe() {
        List<ResearchEvidence> evidence = readyEvidence();
        Fixture fixture = fixture(evidence);

        ResearchGateStatus first = fixture.service().completeResearch(
                fixture.engagement().getId(), fixture.userId());
        ResearchGateStatus replay = fixture.service().completeResearch(
                fixture.engagement().getId(), fixture.userId());

        assertThat(first.researchCompleted()).isTrue();
        assertThat(first.ready()).isTrue();
        assertThat(replay.researchCompleted()).isTrue();
        assertThat(fixture.engagement().getState()).isEqualTo(EngagementState.HYPOTHESIS_READY);
        assertThat(fixture.engagement().getEvents())
                .filteredOn(event -> event.getState() == EngagementState.HYPOTHESIS_READY)
                .hasSize(1);
        verify(fixture.engagements(), times(1)).save(fixture.engagement());
        verify(fixture.evidence(), never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(evidence).hasSize(4);
    }

    private Fixture fixture(List<ResearchEvidence> evidenceRows) {
        UUID userId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        Engagement engagement = Engagement.start(userId, UUID.randomUUID(), UUID.randomUUID());
        engagement.selectLead(leadId);
        EngagementRepository engagements = mock(EngagementRepository.class);
        ResearchEvidenceRepository evidence = mock(ResearchEvidenceRepository.class);
        DifficultyProfileService difficulty = mock(DifficultyProfileService.class);
        when(engagements.findByIdAndUserIdForUpdate(engagement.getId(), userId))
                .thenReturn(Optional.of(engagement));
        when(evidence.findByEngagementId(engagement.getId())).thenReturn(evidenceRows);
        when(difficulty.forEngagement(engagement)).thenReturn(DifficultyProfile.defaults(3, 3, 3, 3));
        LeadService service = new LeadService(mock(LeadRepository.class), evidence, engagements,
                difficulty, mock(ScenarioRepository.class), mock(ScenarioAuthoringConfigService.class));
        return new Fixture(userId, engagement, service, engagements, evidence);
    }

    private List<ResearchEvidence> readyEvidence() {
        UUID engagementId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        return List.of(
                evidence(engagementId, leadId, EvidenceType.STAKEHOLDER_PROFILE, 1),
                evidence(engagementId, leadId, EvidenceType.FINANCIAL_SIGNAL, 2),
                evidence(engagementId, leadId, EvidenceType.TECHNOLOGY_INDICATOR, 3),
                evidence(engagementId, leadId, EvidenceType.HYPOTHESIS, 4));
    }

    private ResearchEvidence evidence(UUID engagementId, UUID leadId, EvidenceType type, int sequence) {
        return ResearchEvidence.builder()
                .engagementId(engagementId)
                .leadId(leadId)
                .note(type == EvidenceType.HYPOTHESIS
                        ? "The client likely needs a staged modernisation programme grounded in the collected evidence."
                        : "Verified client evidence for " + type)
                .evidenceType(type)
                .origin(EvidenceOrigin.SCENARIO_CURATED)
                .verificationStatus(EvidenceVerificationStatus.VERIFIED)
                .confidence(ConfidenceLevel.HIGH)
                .relevanceScore(90)
                .sequenceNo(sequence)
                .build();
    }

    private record Fixture(UUID userId, Engagement engagement, LeadService service,
                           EngagementRepository engagements, ResearchEvidenceRepository evidence) {}
}
