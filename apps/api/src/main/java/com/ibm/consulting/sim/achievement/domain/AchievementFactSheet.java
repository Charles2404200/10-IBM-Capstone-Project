package com.ibm.consulting.sim.achievement.domain;

import java.util.Map;

/**
 * The measurable facts about a learner's history that achievement rules are
 * evaluated against. Always computed fresh from real persisted engagement and
 * assessment data (see {@code AchievementFactSheetBuilder}) — never mocked.
 */
public record AchievementFactSheet(
        int engagementsCompleted,
        int engagementsWon,
        int bestOverallScore,
        double averageOverallScore,
        int distinctScenariosCompleted,
        double winRatePercent,
        Map<String, Integer> bestCompetencyScores) {

    public static AchievementFactSheet empty() {
        return new AchievementFactSheet(0, 0, 0, 0.0, 0, 0.0, Map.of());
    }
}
