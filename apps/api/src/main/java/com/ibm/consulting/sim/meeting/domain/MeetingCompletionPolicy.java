package com.ibm.consulting.sim.meeting.domain;

import java.util.ArrayList;
import java.util.List;

/** A meeting passes only when every relationship dimension reaches the threshold. */
public final class MeetingCompletionPolicy {

    public static final int REQUIRED_SCORE = 70;

    private MeetingCompletionPolicy() {}

    public static MeetingCompletionDecision evaluate(PersonaState state) {
        List<String> unmet = new ArrayList<>();
        if (state.getTrust() < REQUIRED_SCORE) unmet.add("Trust " + state.getTrust() + "/" + REQUIRED_SCORE);
        if (state.getInterest() < REQUIRED_SCORE) unmet.add("Interest " + state.getInterest() + "/" + REQUIRED_SCORE);
        if (state.getPatience() < REQUIRED_SCORE) unmet.add("Patience " + state.getPatience() + "/" + REQUIRED_SCORE);
        return new MeetingCompletionDecision(
                unmet.isEmpty() ? MeetingCompletionOutcome.PASSED : MeetingCompletionOutcome.FAILED,
                List.copyOf(unmet));
    }
}
