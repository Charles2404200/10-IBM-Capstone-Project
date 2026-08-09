package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.ai.domain.PersonaStateDelta;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import com.ibm.consulting.sim.ai.domain.PersonaTurnResponse;

/**
 * Domain service applying a validated AI turn response to persona state.
 * Keeps mutation rules (clamping, idempotent fact disclosure) inside the
 * meeting module rather than leaking them into the AI or application layers.
 */
public final class PersonaStateEngine {

    private PersonaStateEngine() {}

    public static void apply(PersonaState state, PersonaTurnResponse turn) {
        apply(state, turn, null);
    }

    public static void apply(PersonaState state, PersonaTurnResponse turn, DifficultyProfile profile) {
        PersonaStateDelta delta = turn.stateDelta() != null ? turn.stateDelta().clamped() : PersonaStateDelta.zero();
        if (profile != null) delta = scale(delta, profile);
        state.applyClampedDelta(delta);
        turn.factsDisclosed().forEach(state::disclose);
    }

    /**
     * Applies a turn under the live-meeting progression policy. The existing
     * overload remains available for legacy callers and historical replays.
     */
    public static void apply(PersonaState state, PersonaTurnResponse turn, DifficultyProfile profile,
                             String learnerMessage, int learnerTurnNumber) {
        if (profile == null) {
            apply(state, turn);
            return;
        }
        PersonaStateDelta delta = turn.stateDelta() != null ? turn.stateDelta().clamped() : PersonaStateDelta.zero();
        delta = MeetingTurnProgressionPolicy.constrain(
                scale(delta, profile), learnerMessage, turn.detectedLearnerBehaviours());
        state.applyProgressionBoundedDelta(delta, profile, learnerTurnNumber);
        turn.factsDisclosed().forEach(state::disclose);
    }

    private static PersonaStateDelta scale(PersonaStateDelta delta, DifficultyProfile profile) {
        return new PersonaStateDelta(scale(delta.trust(), profile), scale(delta.interest(), profile), scale(delta.patience(), profile));
    }
    private static int scale(int value, DifficultyProfile profile) {
        int urgencyPenalty = profile.timelinePressureDays() <= 14 ? 10
                : profile.timelinePressureDays() <= 21 ? 5 : 0;
        double multiplier = value >= 0 ? profile.scoringTolerance() / 100.0
                : (200 - profile.scoringTolerance() + urgencyPenalty) / 100.0;
        return (int) Math.round(value * multiplier);
    }
}
