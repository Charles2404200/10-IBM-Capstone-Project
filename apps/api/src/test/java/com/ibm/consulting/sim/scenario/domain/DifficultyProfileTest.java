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
        assertThat(hard.meetingTurnLimit()).isLessThan(easy.meetingTurnLimit());
        assertThat(hard.scoringTolerance()).isLessThan(easy.scoringTolerance());
        assertThat(hard.requiredConfidencePercent()).isGreaterThan(easy.requiredConfidencePercent());
        assertThat(hard.proposalEvidenceCoverageThreshold()).isGreaterThan(easy.proposalEvidenceCoverageThreshold());
        assertThat(easy.initialTrust()).isEqualTo(50);
        assertThat(easy.initialInterest()).isEqualTo(50);
        assertThat(easy.initialPatience()).isEqualTo(50);
    }

    @Test
    void appliesSelectedTierWithoutDiscardingScenarioTuning() {
        DifficultyProfile configured = new DifficultyProfile(DifficultyLevel.HARD, 5, 2, 1, 55, 50, 45, 13,
                false, 16, 3, 55, 72, 60, 45, 95);

        DifficultyProfile easyLead = configured.withLevel(DifficultyLevel.EASY);

        assertThat(easyLead.level()).isEqualTo(DifficultyLevel.EASY);
        assertThat(easyLead.meetingTurnLimit()).isEqualTo(configured.meetingTurnLimit());
        assertThat(easyLead.requiredConfidencePercent()).isEqualTo(configured.requiredConfidencePercent());
    }
}
