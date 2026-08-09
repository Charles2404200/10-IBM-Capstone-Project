package com.ibm.consulting.sim.proposal.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProposalDecisionEngineTest {

    @Test
    void doesNotApproveWhenMaterialClaimsAreNotGroundedInEvidence() {
        ProposalDecisionSnapshot snapshot = ProposalDecisionEngine.evaluate(
                completeProposal(List.of()),
                List.of(new ProposalDecisionSource("meeting:1", "The client needs medication reconciliation with controlled rollout.", "MEETING_DISCOVERY")),
                100, 100, 100);

        assertThat(snapshot.outcome()).isIn(
                ClientDecisionOutcome.REVISION_REQUESTED,
                ClientDecisionOutcome.FURTHER_DISCOVERY_REQUIRED);
        assertThat(snapshot.accepted()).isFalse();
        assertThat(snapshot.dimensions()).anySatisfy(dimension -> {
            assertThat(dimension.getDimension()).isEqualTo("Evidence grounding");
            assertThat(dimension.getScore()).isLessThan(45);
        });
    }

    @Test
    void recordsExplainableEvidenceImpactAndConditionsForAnApprovedOutcome() {
        ProposalDecisionSnapshot snapshot = ProposalDecisionEngine.evaluate(
                completeProposal(List.of(new ProposalEvidenceLink("PROBLEM", "meeting:1"))),
                List.of(new ProposalDecisionSource("meeting:1", "The client needs medication reconciliation with controlled rollout and rollback controls.", "MEETING_DISCOVERY")),
                90, 90, 90);

        assertThat(snapshot.outcome()).isIn(ClientDecisionOutcome.PILOT_APPROVED, ClientDecisionOutcome.STRATEGIC_PARTNERSHIP);
        assertThat(snapshot.evidenceImpacts()).isNotEmpty();
        assertThat(snapshot.insights()).anySatisfy(insight -> assertThat(insight.getCategory()).isEqualTo("CONDITION"));
        assertThat(snapshot.learnerPerformanceScore()).isBetween(0, 100);
    }

    private static ProposalDraftContent completeProposal(List<ProposalEvidenceLink> links) {
        return new ProposalDraftContent(
                "Medication reconciliation workflow has patient-safety and audit risks.",
                "Run a controlled medication reconciliation interoperability pilot with rollback controls.",
                List.of("Medication reconciliation integration", "Controlled pilot"),
                new BigDecimal("150000"), 8, "MEDIUM", "Consultant estimate",
                List.of(new ProposalBusinessOutcome("Reduce reconciliation exceptions", "Exception rate", "20%")),
                List.of(new ProposalMilestone("Workflow validation", "Weeks 1-2")),
                List.of(new ProposalRisk("Clinical disruption", "HIGH", "Controlled rollout and rollback threshold")),
                List.of("Clinical SMEs are available"), links);
    }
}
