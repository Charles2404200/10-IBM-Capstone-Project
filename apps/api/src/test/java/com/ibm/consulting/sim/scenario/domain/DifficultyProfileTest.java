package com.ibm.consulting.sim.scenario.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DifficultyProfileTest {

    @Test
    void resolvesCanonicalEasyMediumAndHardDefaults() {
        DifficultyProfile easy = DifficultyProfile.defaults(1, 1, 1, 1);
        DifficultyProfile medium = DifficultyProfile.defaults(3, 3, 3, 3);
        DifficultyProfile hard = DifficultyProfile.defaults(5, 5, 5, 5);

        assertThat(easy).isEqualTo(new DifficultyProfile(DifficultyLevel.EASY, 4, 1, 0,
                50, 50, 50, 14, true, 30, 2, 40, 65, 50, 20, 115));
        assertThat(medium).isEqualTo(new DifficultyProfile(DifficultyLevel.MEDIUM, 5, 2, 1,
                50, 50, 50, 14, false, 18, 3, 60, 75, 65, 50, 100));
        assertThat(hard).isEqualTo(new DifficultyProfile(DifficultyLevel.HARD, 6, 3, 4,
                50, 50, 50, 12, false, 10, 4, 80, 82, 75, 75, 85));
    }

    @Test
    void preservesEveryValidCustomProfileValue() {
        DifficultyProfile custom = new DifficultyProfile(DifficultyLevel.HARD, 8, 7, 6,
                100, 99, 98, 20, true, 90, 8, 90, 95, 95, 100, 130);

        assertThat(custom).isEqualTo(new DifficultyProfile(DifficultyLevel.HARD, 8, 7, 6,
                100, 99, 98, 20, true, 90, 8, 90, 95, 95, 100, 130));
    }

    @Test
    void hardDefaultsRemainSensitiveToScenarioDimensions() {
        DifficultyProfile moderateDimensions = DifficultyProfile.defaults(5, 2, 3, 3);
        DifficultyProfile extremeDimensions = DifficultyProfile.defaults(5, 5, 5, 5);

        assertThat(extremeDimensions.contradictionCount()).isGreaterThan(moderateDimensions.contradictionCount());
        assertThat(extremeDimensions.personaResistance()).isGreaterThan(moderateDimensions.personaResistance());
        assertThat(extremeDimensions.timelinePressureDays()).isLessThan(moderateDimensions.timelinePressureDays());
    }

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
}
