package com.ibm.consulting.sim.meeting.domain;

import java.util.List;

/** Immutable result of the server-side safety gate evaluated after each turn. */
public record MeetingTerminationDecision(
        MeetingTerminationReason reason,
        String message,
        List<String> retryGuidance) {
}
