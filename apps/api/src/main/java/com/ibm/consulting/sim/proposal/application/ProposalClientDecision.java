package com.ibm.consulting.sim.proposal.application;

import com.ibm.consulting.sim.proposal.domain.ProposalDecisionSnapshot;

/** Natural-language rendering of a decision already made by the deterministic outcome engine. */
public record ProposalClientDecision(String message) {
    public static ProposalClientDecision fallback(boolean won) {
        return new ProposalClientDecision(won
                ? "The proposal addresses our priorities well enough to move forward. We will confirm the next delivery step with the sponsor team."
                : "The proposal does not yet give us enough confidence to proceed. We need clearer evidence, commercial rationale and risk controls before revisiting the decision.");
    }

    /** Fast, truthful response used before the optional AI wording enrichment completes. */
    public static ProposalClientDecision fromDecision(ProposalDecisionSnapshot decision) {
        String priority = decision.insights().stream()
                .filter(insight -> "STRENGTH".equals(insight.getCategory()))
                .map(insight -> insight.getDetail())
                .findFirst()
                .orElse("the priorities and delivery constraints we discussed");
        String condition = decision.insights().stream()
                .filter(insight -> "CONDITION".equals(insight.getCategory()))
                .map(insight -> insight.getDetail())
                .findFirst()
                .orElse(null);
        String outcome = decision.accepted()
                ? "We are prepared to move forward. " + priority
                : "We are not ready to proceed. " + priority;
        return new ProposalClientDecision(condition == null ? outcome : outcome + " " + condition);
    }
}
