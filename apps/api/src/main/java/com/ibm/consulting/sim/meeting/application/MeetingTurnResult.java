package com.ibm.consulting.sim.meeting.application;

import java.util.List;

public record MeetingTurnResult(
        ConversationTurnResponse learnerTurn,
        ConversationTurnResponse personaTurn,
        PersonaStateResponse personaState,
        List<String> meetingSignals,
        MeetingTerminationResponse termination,
        MeetingResponseOptionsResponse responseOptions,
        MeetingResponse completedMeeting,
        MeetingBehaviourFeedbackResponse behaviourFeedback) {

    /** Retains the original result contract for existing transport callers. */
    public MeetingTurnResult(ConversationTurnResponse learnerTurn,
                             ConversationTurnResponse personaTurn,
                             PersonaStateResponse personaState,
                             List<String> meetingSignals) {
        this(learnerTurn, personaTurn, personaState, meetingSignals, null, null, null, null);
    }

    public MeetingTurnResult(ConversationTurnResponse learnerTurn,
                             ConversationTurnResponse personaTurn,
                             PersonaStateResponse personaState,
                             List<String> meetingSignals,
                             MeetingTerminationResponse termination) {
        this(learnerTurn, personaTurn, personaState, meetingSignals, termination, null, null, null);
    }

    public MeetingTurnResult(ConversationTurnResponse learnerTurn,
                             ConversationTurnResponse personaTurn,
                             PersonaStateResponse personaState,
                             List<String> meetingSignals,
                             MeetingTerminationResponse termination,
                             MeetingResponseOptionsResponse responseOptions) {
        this(learnerTurn, personaTurn, personaState, meetingSignals, termination, responseOptions, null, null);
    }

    /** Source-compatible constructor for transports compiled before behaviour feedback was added. */
    public MeetingTurnResult(ConversationTurnResponse learnerTurn,
                             ConversationTurnResponse personaTurn,
                             PersonaStateResponse personaState,
                             List<String> meetingSignals,
                             MeetingTerminationResponse termination,
                             MeetingResponseOptionsResponse responseOptions,
                             MeetingResponse completedMeeting) {
        this(learnerTurn, personaTurn, personaState, meetingSignals, termination, responseOptions, completedMeeting, null);
    }
}
