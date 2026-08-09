package com.ibm.consulting.sim.proposal.domain;

import java.util.List;

/** Immutable output of the deterministic client-decision engine. */
public record ProposalDecisionSnapshot(
        ClientDecisionOutcome outcome,
        int decisionScore,
        int decisionConfidence,
        int learnerPerformanceScore,
        List<ProposalDecisionDimension> dimensions,
        List<ProposalDecisionInsight> insights,
        List<ProposalEvidenceImpact> evidenceImpacts,
        String rationale) {
    public ProposalDecisionSnapshot {
        dimensions = List.copyOf(dimensions);
        insights = List.copyOf(insights);
        evidenceImpacts = List.copyOf(evidenceImpacts);
    }
    public boolean accepted() {
        return outcome == ClientDecisionOutcome.PILOT_APPROVED
                || outcome == ClientDecisionOutcome.PROPOSAL_ACCEPTED
                || outcome == ClientDecisionOutcome.STRATEGIC_PARTNERSHIP;
    }
}
