package com.ibm.consulting.sim.proposal.application;

/** Natural-language rendering of a decision already made by the deterministic outcome engine. */
public record ProposalClientDecision(String message) {
    public static ProposalClientDecision fallback(boolean won) {
        return new ProposalClientDecision(won
                ? "The proposal addresses our priorities well enough to move forward. We will confirm the next delivery step with the sponsor team."
                : "The proposal does not yet give us enough confidence to proceed. We need clearer evidence, commercial rationale and risk controls before revisiting the decision.");
    }
}
