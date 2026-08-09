package com.ibm.consulting.sim.proposal.domain;

/** The client-facing simulation outcome. This is distinct from learner performance. */
public enum ClientDecisionOutcome {
    PILOT_APPROVED,
    PROPOSAL_ACCEPTED,
    REVISION_REQUESTED,
    FURTHER_DISCOVERY_REQUIRED,
    DEFERRED,
    REJECTED,
    STRATEGIC_PARTNERSHIP
}
