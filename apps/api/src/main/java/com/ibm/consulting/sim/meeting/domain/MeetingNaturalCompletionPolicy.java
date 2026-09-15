package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import java.util.List;
import java.util.Set;

/**
 * Determines when a client has naturally concluded a successful meeting.
 * A model signal alone is insufficient: the relationship gate and a minimum
 * amount of discovery must also be satisfied.
 */
public final class MeetingNaturalCompletionPolicy {

    private static final int MINIMUM_LEARNER_TURNS = 3;
    private static final Set<String> CLOSING_SIGNALS = Set.of(
            "client_ready_to_close",
            "client_committed_next_step");

    private MeetingNaturalCompletionPolicy() {
    }

    public static boolean shouldConclude(PersonaState state, List<String> signals, int learnerTurnCount) {
        return shouldConclude(state, null, signals, learnerTurnCount);
    }

    public static boolean shouldConclude(PersonaState state, DifficultyProfile profile, List<String> signals,
                                         int learnerTurnCount) {
        if (learnerTurnCount < MINIMUM_LEARNER_TURNS || !MeetingCompletionPolicy.evaluate(state, profile).passed()) {
            return false;
        }
        return signals != null && signals.stream().anyMatch(CLOSING_SIGNALS::contains);
    }
}
