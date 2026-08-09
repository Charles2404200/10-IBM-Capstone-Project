package com.ibm.consulting.sim.scenario.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DifficultyProfileTest {

    @Test
    void hardProfileCreatesMeaningfullyTighterGameplayThanEasy() {
        DifficultyProfile easy = DifficultyProfile.defaults(1, 1, 1, 1);
        DifficultyProfile hard = DifficultyProfile.defaults(5, 5, 5, 5);

        assertThat(hard.distractorArtifactsPerAction()).isGreaterThan(easy.distractorArtifactsPerAction());
        assertThat(hard.contradictionCount()).isGreaterThan(easy.contradictionCount());
        assertThat(hard.initialPatience()).isLessThan(easy.initialPatience());
        assertThat(hard.meetingTurnLimit()).isLessThan(easy.meetingTurnLimit());
        assertThat(hard.requiredConfidencePercent()).isGreaterThan(easy.requiredConfidencePercent());
        assertThat(hard.proposalEvidenceCoverageThreshold()).isGreaterThan(easy.proposalEvidenceCoverageThreshold());
    }
}
