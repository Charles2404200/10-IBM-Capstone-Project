package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.scenario.domain.DifficultyLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingInteractionModeTest {

    @Test
    void guidesEasyAndMediumMeetingsButKeepsHardFreeform() {
        assertThat(MeetingInteractionMode.forDifficulty(DifficultyLevel.EASY)).isEqualTo(MeetingInteractionMode.GUIDED);
        assertThat(MeetingInteractionMode.forDifficulty(DifficultyLevel.MEDIUM)).isEqualTo(MeetingInteractionMode.GUIDED);
        assertThat(MeetingInteractionMode.forDifficulty(DifficultyLevel.HARD)).isEqualTo(MeetingInteractionMode.FREEFORM);
    }
}
