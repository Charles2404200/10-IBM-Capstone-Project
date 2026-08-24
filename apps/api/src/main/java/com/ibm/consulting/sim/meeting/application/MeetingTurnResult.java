package com.ibm.consulting.sim.meeting.application;

import java.util.List;

public record MeetingTurnResult(
        ConversationTurnResponse learnerTurn,
        ConversationTurnResponse personaTurn,
        PersonaStateResponse personaState,
        List<String> meetingSignals,
        MeetingTerminationResponse termination,
        MeetingResponseOptionsResponse responseOptions,
        MeetingResponse completedMeeting) {

    /** Retains the original result contract for existing transport callers. */
    public MeetingTurnResult(ConversationTurnResponse learnerTurn,
                             ConversationTurnResponse personaTurn,
                             PersonaStateResponse personaState,
                             List<String> meetingSignals) {
        this(learnerTurn, personaTurn, personaState, meetingSignals, null, null, null);
    }

    public MeetingTurnResult(ConversationTurnResponse learnerTurn,
                             ConversationTurnResponse personaTurn,
                             PersonaStateResponse personaState,
                             List<String> meetingSignals,
                             MeetingTerminationResponse termination) {
        this(learnerTurn, personaTurn, personaState, meetingSignals, termination, null, null);
    }

    public MeetingTurnResult(ConversationTurnResponse learnerTurn,
                             ConversationTurnResponse personaTurn,
                             PersonaStateResponse personaState,
                             List<String> meetingSignals,
                             MeetingTerminationResponse termination,
                             MeetingResponseOptionsResponse responseOptions) {
        this(learnerTurn, personaTurn, personaState, meetingSignals, termination, responseOptions, null);
    }
}
