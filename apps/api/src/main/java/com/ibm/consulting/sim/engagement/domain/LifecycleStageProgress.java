package com.ibm.consulting.sim.engagement.domain;

import com.ibm.consulting.sim.scenario.domain.StageCapability;

/** Maps technical milestones without treating MEETING_FAILED as downstream success. */
public final class LifecycleStageProgress {
    private LifecycleStageProgress() {}
    public static StageCapability current(EngagementState state) {
        return StageCapability.valueOf(EngagementProgressCalculator.phaseOf(state).name());
    }
    public static boolean completed(StageCapability capability, EngagementState state, LifecycleFacts facts) {
        if (capability == StageCapability.COMPLETED) return state == EngagementState.COMPLETED;
        if (capability == StageCapability.MEETING_REVIEW) {
            return state != EngagementState.MEETING_FAILED && current(state).ordinal() > capability.ordinal()
                    || facts.meetingCompleted() && state == EngagementState.MEETING_FAILED;
        }
        if (capability == StageCapability.LIVE_MEETING && facts.meetingCompleted()) return true;
        if (state == EngagementState.MEETING_FAILED) return capability.ordinal() < StageCapability.LIVE_MEETING.ordinal();
        if (capability == StageCapability.CLIENT_INTELLIGENCE && state == EngagementState.HYPOTHESIS_READY) return true;
        if (capability == StageCapability.PROPOSAL && state == EngagementState.PROPOSAL_SUBMITTED) return true;
        return current(state).ordinal() > capability.ordinal();
    }
}
