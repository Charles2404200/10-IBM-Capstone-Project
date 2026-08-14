package com.ibm.consulting.sim.lead.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scripted learner journeys. These are deliberately phrased as human behaviour
 * rather than implementation details, so regressions in the training experience
 * are caught alongside ordinary unit tests.
 */
class ResearchHumanJourneySimulationTest {

    private int sequence;

    @Test
    void evidenceLedConsultantUnlocksOutreach() {
        ResearchEvidence company = finding(EvidenceType.COMPANY_NEWS, "Public signals show delivery pressure.", 86);
        ResearchEvidence stakeholder = finding(EvidenceType.STAKEHOLDER_PROFILE, "Priya Nathan owns the operational decision.", 92);
        ResearchEvidence technology = finding(EvidenceType.TECHNOLOGY_INDICATOR, "Legacy WMS limits operational visibility.", 88);
        ResearchEvidence hypothesis = hypothesis(Set.of(company.getId(), stakeholder.getId(), technology.getId()));

        List<ResearchEvidence> journey = List.of(company, stakeholder, technology, hypothesis);
        var quality = ResearchReadinessPolicy.assess(journey);

        assertThat(quality.confidencePercent()).isGreaterThanOrEqualTo(70);
        assertThat(quality.groundedHypothesis()).isTrue();
        assertThat(ResearchReadinessPolicy.isResearchComplete(journey)).isTrue();
    }

    @Test
    void collectorOfNoiseDoesNotEarnConfidenceFromVolume() {
        List<ResearchEvidence> noise = List.of(
                unverified(EvidenceType.COMPANY_NEWS, "Adjacent market article one"),
                unverified(EvidenceType.COMPANY_NEWS, "Adjacent market article two"),
                unverified(EvidenceType.COMPANY_NEWS, "Adjacent market article three"),
                unverified(EvidenceType.COMPANY_NEWS, "Adjacent market article four"));

        var quality = ResearchReadinessPolicy.assess(noise);

        assertThat(quality.coverageCount()).isEqualTo(1);
        assertThat(quality.confidencePercent()).isLessThan(40);
        assertThat(ResearchReadinessPolicy.isResearchComplete(noise)).isFalse();
    }

    @Test
    void externalClaimDoesNotRevealCanonicalDecisionMakerUntilCorroborated() {
        ResearchEvidence externalClaim = ResearchEvidence.builder()
                .engagementId(UUID.randomUUID())
                .leadId(UUID.randomUUID())
                .note("The CFO is definitely the decision maker.")
                .evidenceType(EvidenceType.STAKEHOLDER_PROFILE)
                .confidence(ConfidenceLevel.HIGH)
                .origin(EvidenceOrigin.USER_SUPPLIED)
                .verificationStatus(EvidenceVerificationStatus.UNVERIFIED)
                .relevanceScore(45)
                .sequenceNo(1)
                .build();

        assertThat(LeadIntelligencePolicy.decisionMaker(List.of(externalClaim)).value()).isNull();
        assertThat(ResearchReadinessPolicy.hasStakeholderEvidence(List.of(externalClaim))).isFalse();
    }

    private ResearchEvidence finding(EvidenceType type, String note, int relevance) {
        return ResearchEvidence.builder()
                .engagementId(UUID.randomUUID())
                .leadId(UUID.randomUUID())
                .note(note)
                .evidenceType(type)
                .confidence(ConfidenceLevel.HIGH)
                .origin(EvidenceOrigin.AI_SYNTHESIZED)
                .verificationStatus(EvidenceVerificationStatus.CORROBORATED)
                .relevanceScore(relevance)
                .sequenceNo(++sequence)
                .build();
    }

    private ResearchEvidence hypothesis(Set<UUID> supportingIds) {
        return ResearchEvidence.builder()
                .engagementId(UUID.randomUUID())
                .leadId(UUID.randomUUID())
                .note("Fragmented operational systems appear to create delivery risk; a contained pilot should validate the root cause and impact.")
                .hypothesis("Fragmented operational systems appear to create delivery risk.")
                .evidenceType(EvidenceType.HYPOTHESIS)
                .confidence(ConfidenceLevel.MEDIUM)
                .sequenceNo(99_999)
                .supportingEvidenceIds(supportingIds)
                .build();
    }

    private ResearchEvidence unverified(EvidenceType type, String note) {
        return ResearchEvidence.builder()
                .engagementId(UUID.randomUUID())
                .leadId(UUID.randomUUID())
                .note(note)
                .evidenceType(type)
                .confidence(ConfidenceLevel.LOW)
                .origin(EvidenceOrigin.USER_SUPPLIED)
                .verificationStatus(EvidenceVerificationStatus.UNVERIFIED)
                .relevanceScore(20)
                .sequenceNo(++sequence)
                .build();
    }
}
