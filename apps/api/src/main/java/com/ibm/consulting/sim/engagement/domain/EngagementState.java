package com.ibm.consulting.sim.engagement.domain;

public enum EngagementState {
    QUALIFYING,
    CLIENT_INTELLIGENCE,
    HYPOTHESIS_READY,
    OUTREACHING,
    MEETING_SECURED,
    PREPARING,
    IN_MEETING,
    MEETING_FAILED,
    DISCOVERY_COMPLETE,
    PROPOSAL_DRAFT,
    PROPOSAL_SUBMITTED,
    CLIENT_DECISION,
    REVIEW,
    COMPLETED;

    public boolean isTerminal() {
        return this == COMPLETED || this == MEETING_FAILED;
    }
}
