package com.ibm.consulting.sim.meeting.application;

import com.ibm.consulting.sim.meeting.domain.Meeting;
import com.ibm.consulting.sim.meeting.domain.MeetingTerminationDecision;

import java.util.List;

/** API-safe projection of a deterministic automatic meeting termination. */
public record MeetingTerminationResponse(String reason, String message, List<String> retryGuidance) {

    static MeetingTerminationResponse from(MeetingTerminationDecision decision) {
        return new MeetingTerminationResponse(
                decision.reason().name(), decision.message(), List.copyOf(decision.retryGuidance()));
    }

    static MeetingTerminationResponse from(Meeting meeting) {
        if (meeting.getTerminationReason() == null) {
            return null;
        }
        return new MeetingTerminationResponse(
                meeting.getTerminationReason().name(),
                meeting.getTerminationMessage(),
                List.copyOf(meeting.getDebriefTips()));
    }
}
