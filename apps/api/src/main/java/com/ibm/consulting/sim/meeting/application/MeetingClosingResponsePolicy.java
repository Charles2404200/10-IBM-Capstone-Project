package com.ibm.consulting.sim.meeting.application;

import com.ibm.consulting.sim.ai.domain.PersonaTurnResponse;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Enforces the simulation engine's closing decision if a dialogue provider
 * ignores the final-turn contract and tries to prolong a passed meeting.
 */
final class MeetingClosingResponsePolicy {

    private static final String CLOSING_RESPONSE = "That gives me enough confidence to take the agreed next step forward. "
            + "Please send the concise summary we discussed, and I will coordinate the follow-up with the right people. "
            + "Thank you for the conversation; I look forward to reconnecting next week.";

    private MeetingClosingResponsePolicy() {
    }

    static PersonaTurnResponse conclude(PersonaTurnResponse response) {
        LinkedHashSet<String> signals = new LinkedHashSet<>(response.meetingSignals());
        signals.remove("client_concern_raised");
        signals.add("client_committed_next_step");
        signals.add("client_ready_to_close");

        return new PersonaTurnResponse(
                CLOSING_RESPONSE,
                response.detectedLearnerBehaviours(),
                response.stateDelta(),
                response.factsDisclosed(),
                null,
                List.copyOf(signals),
                response.safety(),
                List.of());
    }
}
