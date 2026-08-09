package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.scenario.domain.DifficultyLevel;

/** Learner input contract selected from the immutable engagement difficulty snapshot. */
public enum MeetingInteractionMode {
    GUIDED,
    FREEFORM;

    public static MeetingInteractionMode forDifficulty(DifficultyLevel difficulty) {
        return difficulty == DifficultyLevel.HARD ? FREEFORM : GUIDED;
    }
}
