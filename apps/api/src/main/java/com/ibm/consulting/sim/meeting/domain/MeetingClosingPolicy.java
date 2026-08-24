package com.ibm.consulting.sim.meeting.domain;

/**
 * Determines when a meeting has moved into its final confirmation exchange.
 *
 * <p>Passing the relationship gate makes the client ready to conclude. It does
 * not give the dialogue model permission to keep raising new objections.</p>
 */
public final class MeetingClosingPolicy {

    static final int MINIMUM_LEARNER_TURNS = 3;

    private MeetingClosingPolicy() {
    }

    /** True when the next client response must conclude rather than open new discovery. */
    public static boolean requiresConclusionAfterReply(PersonaState state, int learnerTurnsAlreadyCompleted) {
        return learnerTurnsAlreadyCompleted >= MINIMUM_LEARNER_TURNS
                && MeetingCompletionPolicy.evaluate(state).passed();
    }

    /** The learner's final confirmation must not have undone the relationship gate. */
    public static boolean canConclude(PersonaState state, boolean conclusionWasRequired,
                                      int learnerTurnsIncludingCurrent) {
        return conclusionWasRequired
                && learnerTurnsIncludingCurrent >= MINIMUM_LEARNER_TURNS
                && MeetingCompletionPolicy.evaluate(state).passed();
    }
}
