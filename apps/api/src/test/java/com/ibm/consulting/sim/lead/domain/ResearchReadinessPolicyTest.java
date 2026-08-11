package com.ibm.consulting.sim.lead.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchReadinessPolicyTest {

    private ResearchEvidence evidence(EvidenceType type) {
        return ResearchEvidence.builder()
                .engagementId(java.util.UUID.randomUUID())
                .leadId(java.util.UUID.randomUUID())
                .note(type == EvidenceType.HYPOTHESIS
                        ? "Fragmented operational systems appear to create a material client problem that requires validation."
                        : "finding")
                .evidenceType(type)
                .confidence(ConfidenceLevel.MEDIUM)
                .origin(EvidenceOrigin.AI_SYNTHESIZED)
                .verificationStatus(EvidenceVerificationStatus.CORROBORATED)
                .relevanceScore(80)
                .sequenceNo(1)
                .build();
    }

    @Test
    void noEvidenceIsNotComplete() {
        assertThat(ResearchReadinessPolicy.isResearchComplete(List.of())).isFalse();
        assertThat(ResearchReadinessPolicy.confidencePercent(List.of())).isZero();
    }

    @Test
    void hypothesisEvidenceDoesNotCountTowardEvidenceCount() {
        List<ResearchEvidence> evidence = List.of(
                evidence(EvidenceType.COMPANY_NEWS),
                evidence(EvidenceType.HYPOTHESIS));
        assertThat(ResearchReadinessPolicy.evidenceCount(evidence)).isEqualTo(1);
    }

    @Test
    void requiresStakeholderEvidenceEvenWithEnoughVolume() {
        List<ResearchEvidence> evidence = List.of(
                evidence(EvidenceType.COMPANY_NEWS),
                evidence(EvidenceType.FINANCIAL_SIGNAL),
                evidence(EvidenceType.TECHNOLOGY_INDICATOR),
                evidence(EvidenceType.HYPOTHESIS));
        assertThat(ResearchReadinessPolicy.hasStakeholderEvidence(evidence)).isFalse();
        assertThat(ResearchReadinessPolicy.isResearchComplete(evidence)).isFalse();
    }

    @Test
    void requiresHypothesisEvenWithEnoughVolumeAndStakeholder() {
        List<ResearchEvidence> evidence = List.of(
                evidence(EvidenceType.COMPANY_NEWS),
                evidence(EvidenceType.FINANCIAL_SIGNAL),
                evidence(EvidenceType.STAKEHOLDER_PROFILE));
        assertThat(ResearchReadinessPolicy.hasHypothesis(evidence)).isFalse();
        assertThat(ResearchReadinessPolicy.isResearchComplete(evidence)).isFalse();
    }

    @Test
    void completeWhenAllConditionsSatisfied() {
        List<ResearchEvidence> evidence = List.of(
                evidence(EvidenceType.COMPANY_NEWS),
                evidence(EvidenceType.FINANCIAL_SIGNAL),
                evidence(EvidenceType.STAKEHOLDER_PROFILE),
                evidence(EvidenceType.HYPOTHESIS));
        assertThat(ResearchReadinessPolicy.evidenceCount(evidence)).isEqualTo(3);
        assertThat(ResearchReadinessPolicy.confidencePercent(evidence)).isGreaterThanOrEqualTo(40);
        assertThat(ResearchReadinessPolicy.isResearchComplete(evidence)).isTrue();
    }

    @Test
    void confidencePercentNeverExceedsOneHundred() {
        List<ResearchEvidence> evidence = List.of(
                evidence(EvidenceType.COMPANY_NEWS),
                evidence(EvidenceType.COMPANY_NEWS),
                evidence(EvidenceType.COMPANY_NEWS),
                evidence(EvidenceType.COMPANY_NEWS),
                evidence(EvidenceType.COMPANY_NEWS),
                evidence(EvidenceType.COMPANY_NEWS));
        assertThat(ResearchReadinessPolicy.confidencePercent(evidence)).isLessThan(100);
    }
}
