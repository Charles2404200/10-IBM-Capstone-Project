package com.ibm.consulting.sim.scenario.domain;

import java.util.Optional;

/** Declarative condition allowlist. Each condition identifies when its backing data first exists. */
public enum LifecycleConditionType {
    LEAD_SELECTED(StageCapability.LEAD, false),
    MIN_EVIDENCE_COUNT(StageCapability.CLIENT_INTELLIGENCE, false),
    HAS_HYPOTHESIS(StageCapability.CLIENT_INTELLIGENCE, false),
    MIN_RESEARCH_CONFIDENCE(StageCapability.CLIENT_INTELLIGENCE, false),
    OUTREACH_ACCEPTED(StageCapability.OUTREACH, false),
    PREPARATION_READY(StageCapability.MEETING_PREPARATION, false),
    MIN_PREPARATION_SCORE(StageCapability.MEETING_PREPARATION, false),
    MEETING_STARTED(StageCapability.LIVE_MEETING, true),
    MEETING_COMPLETED(StageCapability.LIVE_MEETING, false),
    MIN_TRUST(StageCapability.LIVE_MEETING, true),
    MIN_INTEREST(StageCapability.LIVE_MEETING, true),
    MIN_PATIENCE(StageCapability.LIVE_MEETING, true),
    PROPOSAL_CREATED(StageCapability.PROPOSAL, true),
    PROPOSAL_SUBMITTED(StageCapability.PROPOSAL, false),
    CLIENT_DECISION_AVAILABLE(StageCapability.OUTCOME, true),
    ASSESSMENT_AVAILABLE(StageCapability.REVIEW, true),
    CURRENT_STAGE_COMPLETED(null, false);

    private final StageCapability availableFrom;
    private final boolean availableAtStageEntry;

    LifecycleConditionType(StageCapability availableFrom, boolean availableAtStageEntry) {
        this.availableFrom = availableFrom;
        this.availableAtStageEntry = availableAtStageEntry;
    }

    /** Earliest capability whose runtime data can evaluate this condition; empty means objective-stage context. */
    public Optional<StageCapability> availableFrom() {
        return Optional.ofNullable(availableFrom);
    }

    /** Whether backing data exists before entering the earliest capability rather than during its work. */
    public boolean availableAtStageEntry() {
        return availableAtStageEntry;
    }
}
