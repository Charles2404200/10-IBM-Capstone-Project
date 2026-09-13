package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.scenario.domain.DifficultyLevel;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;

import java.util.ArrayList;
import java.util.List;

/** A meeting passes only when every relationship dimension reaches the threshold. */
public final class MeetingCompletionPolicy {

    /** Default gate retained for Easy and Medium meetings. */
    public static final int REQUIRED_SCORE = 70;
    public static final int HARD_REQUIRED_SCORE = 80;

    private MeetingCompletionPolicy() {}

    public static MeetingCompletionDecision evaluate(PersonaState state) {
        return evaluate(state, null);
    }

    public static MeetingCompletionDecision evaluate(PersonaState state, DifficultyProfile profile) {
        int requiredScore = requiredScoreFor(profile);
        List<String> unmet = new ArrayList<>();
        if (state.getTrust() < requiredScore) unmet.add("Trust " + state.getTrust() + "/" + requiredScore);
        if (state.getInterest() < requiredScore) unmet.add("Interest " + state.getInterest() + "/" + requiredScore);
        if (state.getPatience() < requiredScore) unmet.add("Patience " + state.getPatience() + "/" + requiredScore);
        return new MeetingCompletionDecision(
                unmet.isEmpty() ? MeetingCompletionOutcome.PASSED : MeetingCompletionOutcome.FAILED,
                List.copyOf(unmet));
    }

    public static int requiredScoreFor(DifficultyProfile profile) {
        return profile != null && profile.level() == DifficultyLevel.HARD
                ? HARD_REQUIRED_SCORE
                : REQUIRED_SCORE;
    }
}
