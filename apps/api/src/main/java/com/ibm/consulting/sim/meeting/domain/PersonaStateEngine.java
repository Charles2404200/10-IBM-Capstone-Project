package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.ai.domain.PersonaStateDelta;
import com.ibm.consulting.sim.ai.domain.PersonaTurnResponse;

/**
 * Domain service applying a validated AI turn response to persona state.
 * Keeps mutation rules (clamping, idempotent fact disclosure) inside the
 * meeting module rather than leaking them into the AI or application layers.
 */
public final class PersonaStateEngine {

    private PersonaStateEngine() {}

    public static void apply(PersonaState state, PersonaTurnResponse turn) {
        PersonaStateDelta delta = turn.stateDelta() != null ? turn.stateDelta().clamped() : PersonaStateDelta.zero();
        state.applyClampedDelta(delta);
        turn.factsDisclosed().forEach(state::disclose);
    }
}
