package com.ibm.consulting.sim.meeting.application;

import com.ibm.consulting.sim.meeting.domain.MeetingInteractionMode;
import com.ibm.consulting.sim.meeting.domain.MeetingResponseOptionSet;

import java.util.List;

/** Read model for the current learner input mode and, when guided, its choices. */
public record MeetingResponseOptionsResponse(
        MeetingInteractionMode interactionMode,
        int sourceSequence,
        List<String> options,
        boolean available,
        String unavailableReason) {

    public MeetingResponseOptionsResponse {
        options = options == null ? List.of() : List.copyOf(options);
    }

    static MeetingResponseOptionsResponse freeform() {
        return new MeetingResponseOptionsResponse(MeetingInteractionMode.FREEFORM, 0, List.of(), false, null);
    }

    static MeetingResponseOptionsResponse unavailable(int sourceSequence, String reason) {
        return new MeetingResponseOptionsResponse(MeetingInteractionMode.GUIDED, sourceSequence, List.of(), false, reason);
    }

    static MeetingResponseOptionsResponse from(MeetingResponseOptionSet optionSet) {
        return new MeetingResponseOptionsResponse(MeetingInteractionMode.GUIDED, optionSet.getSourceSequence(),
                optionSet.getOptions(), true, null);
    }
}
