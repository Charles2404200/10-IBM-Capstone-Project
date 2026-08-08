package com.ibm.consulting.sim.ai.domain;

/**
 * Bounded change to persona relationship state for a single conversational turn.
 * Values are clamped to the configured per-turn limits before being applied —
 * the AI must never move relationship state by an unbounded amount (§5.2, §5.4).
 */
public record PersonaStateDelta(int trust, int interest, int patience) {

    private static final int MAX_ABS_DELTA = 10;

    public static PersonaStateDelta zero() {
        return new PersonaStateDelta(0, 0, 0);
    }

    /** Returns a new delta with every component clamped to [-MAX_ABS_DELTA, MAX_ABS_DELTA]. */
    public PersonaStateDelta clamped() {
        return new PersonaStateDelta(clamp(trust), clamp(interest), clamp(patience));
    }

    private static int clamp(int value) {
        return Math.max(-MAX_ABS_DELTA, Math.min(MAX_ABS_DELTA, value));
    }
}
