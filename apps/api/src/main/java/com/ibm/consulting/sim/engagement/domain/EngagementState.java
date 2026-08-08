package com.ibm.consulting.sim.engagement.domain;

public enum EngagementState {
    DRAFT,
    LEAD_SELECTED,
    RESEARCH_COMPLETED,
    OUTREACH_IN_PROGRESS,
    MEETING_SECURED,
    OUTREACH_FAILED,
    PREPARATION_COMPLETED,
    MEETING_IN_PROGRESS,
    MEETING_COMPLETED,
    PROPOSAL_SUBMITTED,
    CONTRACT_WON,
    CONTRACT_LOST,
    REVIEW_AVAILABLE,
    ARCHIVED;

    public boolean isTerminal() {
        return this == ARCHIVED;
    }
}
