package com.ibm.consulting.sim.proposal.domain;

public record ProposalOutcome(int alignmentScore, boolean won, String rationale) {
}
