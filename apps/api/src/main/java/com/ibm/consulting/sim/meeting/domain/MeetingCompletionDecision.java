package com.ibm.consulting.sim.meeting.domain;

import java.util.List;

/** Server-authoritative completion decision for a live meeting. */
public record MeetingCompletionDecision(
        MeetingCompletionOutcome outcome,
        List<String> unmetRequirements) {

    public boolean passed() {
        return outcome == MeetingCompletionOutcome.PASSED;
    }
}
