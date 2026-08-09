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
        if (profile != null) delta = scale(delta, profile.scoringTolerance());
        state.applyClampedDelta(delta);
        turn.factsDisclosed().forEach(state::disclose);
    }

    private static PersonaStateDelta scale(PersonaStateDelta delta, int tolerance) {
        return new PersonaStateDelta(scale(delta.trust(), tolerance), scale(delta.interest(), tolerance), scale(delta.patience(), tolerance));
    }
    private static int scale(int value, int tolerance) {
        double multiplier = value >= 0 ? tolerance / 100.0 : (200 - tolerance) / 100.0;
        return (int) Math.round(value * multiplier);
    }
}
