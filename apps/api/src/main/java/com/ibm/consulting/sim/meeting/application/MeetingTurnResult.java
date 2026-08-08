package com.ibm.consulting.sim.meeting.application;

import java.util.List;

public record MeetingTurnResult(
        ConversationTurnResponse learnerTurn,
        ConversationTurnResponse personaTurn,
        PersonaStateResponse personaState,
        List<String> meetingSignals) {
}
