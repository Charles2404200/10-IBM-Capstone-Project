package com.ibm.consulting.sim.lead.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LeadIntelligencePolicyTest {

    private int seq = 0;

    private ResearchEvidence evidence(EvidenceType type, String note, ConfidenceLevel confidence) {
        return ResearchEvidence.builder()
                .engagementId(UUID.randomUUID())
                .leadId(UUID.randomUUID())
                .note(note)
                .evidenceType(type)
                .confidence(confidence)
                .sequenceNo(++seq)
                .build();
    }

    private ResearchEvidence evidence(EvidenceType type) {
        return evidence(type, "finding", ConfidenceLevel.MEDIUM);
    }

    @Test
    void noEvidenceRevealsNothing() {
        List<ResearchEvidence> evidence = List.of();
        assertThat(LeadIntelligencePolicy.decisionMaker(evidence).value()).isNull();
        assertThat(LeadIntelligencePolicy.technologyStack(evidence).value()).isNull();
        assertThat(LeadIntelligencePolicy.budgetSignal(evidence).value()).isNull();
        assertThat(LeadIntelligencePolicy.painSeverity(evidence).value()).isNull();
        assertThat(LeadIntelligencePolicy.potentialValue(evidence, "$1M").value()).isNull();
        assertThat(LeadIntelligencePolicy.confidenceLabel(evidence)).isEqualTo("LOW");
    }

    @Test
    void decisionMakerIsDerivedFromStakeholderEvidenceContentNotAPreset() {
        List<ResearchEvidence> evidence = List.of(
                evidence(EvidenceType.STAKEHOLDER_PROFILE, "Sarah Chen, VP of Supply Chain, sponsors modernisation", ConfidenceLevel.HIGH));
        var insight = LeadIntelligencePolicy.decisionMaker(evidence);
        assertThat(insight.value()).isEqualTo("Sarah Chen, VP of Supply Chain, sponsors modernisation");
        assertThat(insight.supportingEvidenceSequence()).containsExactly(1);
    }

    @Test
    void technologyStackIsDerivedFromTechnologyEvidenceContent() {
        List<ResearchEvidence> evidence = List.of(
                evidence(EvidenceType.TECHNOLOGY_INDICATOR, "Legacy warehouse systems and spreadsheets", ConfidenceLevel.HIGH));
        assertThat(LeadIntelligencePolicy.technologyStack(evidence).value())
                .isEqualTo("Legacy warehouse systems and spreadsheets");
        assertThat(LeadIntelligencePolicy.painSeverity(evidence).value()).isNull();
    }

    @Test
    void latestMatchingEvidenceIsTheCurrentValueButAllMatchesAreCited() {
        List<ResearchEvidence> evidence = List.of(
                evidence(EvidenceType.STAKEHOLDER_PROFILE, "Early lead: unnamed ops manager", ConfidenceLevel.LOW),
                evidence(EvidenceType.STAKEHOLDER_PROFILE, "Confirmed: Sarah Chen is the actual sponsor", ConfidenceLevel.HIGH));
        var insight = LeadIntelligencePolicy.decisionMaker(evidence);
        assertThat(insight.value()).isEqualTo("Confirmed: Sarah Chen is the actual sponsor");
        assertThat(insight.supportingEvidenceSequence()).containsExactly(1, 2);
    }

    @Test
    void companyNewsOrMarketTrendRevealsPainSeverity() {
        assertThat(LeadIntelligencePolicy.painSeverity(List.of(evidence(EvidenceType.COMPANY_NEWS))).value()).isNotNull();
        assertThat(LeadIntelligencePolicy.painSeverity(List.of(evidence(EvidenceType.MARKET_TREND))).value()).isNotNull();
    }

    @Test
    void potentialValueRequiresBothStakeholderAndFinancialEvidenceAndCitesBoth() {
        List<ResearchEvidence> stakeholderOnly = List.of(evidence(EvidenceType.STAKEHOLDER_PROFILE));
        assertThat(LeadIntelligencePolicy.potentialValue(stakeholderOnly, "$1M").value()).isNull();

        List<ResearchEvidence> both = List.of(
                evidence(EvidenceType.STAKEHOLDER_PROFILE),
                evidence(EvidenceType.FINANCIAL_SIGNAL));
        var insight = LeadIntelligencePolicy.potentialValue(both, "$500K - $1.2M");
        assertThat(insight.value()).isEqualTo("$500K - $1.2M");
        assertThat(insight.supportingEvidenceSequence()).containsExactlyInAnyOrder(2, 3);
    }

    @Test
    void confidenceScoreRewardsBreadthOfCoverageOverVolume() {
        // Ten low-value notes in a single category must NOT reach HIGH.
        List<ResearchEvidence> spam = List.of(
                evidence(EvidenceType.COMPANY_NEWS, "n1", ConfidenceLevel.LOW),
                evidence(EvidenceType.COMPANY_NEWS, "n2", ConfidenceLevel.LOW),
                evidence(EvidenceType.COMPANY_NEWS, "n3", ConfidenceLevel.LOW),
                evidence(EvidenceType.COMPANY_NEWS, "n4", ConfidenceLevel.LOW),
                evidence(EvidenceType.COMPANY_NEWS, "n5", ConfidenceLevel.LOW));
        assertThat(LeadIntelligencePolicy.confidenceLabel(spam)).isEqualTo("LOW");

        List<ResearchEvidence> broadHighReliability = List.of(
                evidence(EvidenceType.COMPANY_NEWS, "n1", ConfidenceLevel.HIGH),
                evidence(EvidenceType.STAKEHOLDER_PROFILE, "n2", ConfidenceLevel.HIGH),
                evidence(EvidenceType.FINANCIAL_SIGNAL, "n3", ConfidenceLevel.HIGH),
                evidence(EvidenceType.TECHNOLOGY_INDICATOR, "n4", ConfidenceLevel.HIGH));
        assertThat(LeadIntelligencePolicy.confidenceLabel(broadHighReliability)).isEqualTo("HIGH");
    }

    @Test
    void hypothesisEvidenceDoesNotCountTowardCoverageOrReliability() {
        List<ResearchEvidence> evidence = List.of(
                evidence(EvidenceType.COMPANY_NEWS, "n1", ConfidenceLevel.HIGH),
                evidence(EvidenceType.HYPOTHESIS, "synthesis", ConfidenceLevel.HIGH));
        assertThat(LeadIntelligencePolicy.coverageCount(evidence)).isEqualTo(1);
        assertThat(LeadIntelligencePolicy.averageReliability(evidence)).isEqualTo(100);
    }

    @Test
    void confidenceFactorsDescribeCoverageAndReliabilityBreakdown() {
        List<ResearchEvidence> evidence = List.of(
                evidence(EvidenceType.COMPANY_NEWS, "n1", ConfidenceLevel.HIGH),
                evidence(EvidenceType.STAKEHOLDER_PROFILE, "n2", ConfidenceLevel.MEDIUM));
        List<String> factors = LeadIntelligencePolicy.confidenceFactors(evidence);
        assertThat(factors).contains("2/4 research areas covered");
        assertThat(factors).anyMatch(f -> f.contains("high-reliability finding"));
        assertThat(factors).anyMatch(f -> f.contains("medium-reliability finding"));
    }
}
