package com.ibm.consulting.sim.achievement.domain;

import java.util.List;

/**
 * Evaluates an {@link AchievementCondition} tree against an {@link AchievementFactSheet},
 * producing both a boolean "unlocked?" result and a 0-100 progress percentage suitable
 * for a progress-bar UI. Pure domain logic — deterministic, no external dependencies,
 * mirrors the same "no AI/no side effects" discipline as {@code AssessmentEngine}.
 */
public final class AchievementRuleEvaluator {

    private AchievementRuleEvaluator() {}

    public static boolean isSatisfied(AchievementCondition condition, AchievementFactSheet facts) {
        return progress(condition, facts) >= 100.0;
    }

    /**
     * Progress toward satisfying {@code condition}, from 0 to 100. For a group, AND
     * progress is the minimum of its children (every child must reach 100), OR
     * progress is the maximum (any single child reaching 100 satisfies the group).
     */
    public static double progress(AchievementCondition condition, AchievementFactSheet facts) {
        if (condition instanceof ConditionGroup group) {
            List<Double> childProgress = group.children().stream()
                    .map(child -> progress(child, facts))
                    .toList();
            return switch (group.operator()) {
                case AND -> childProgress.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
                case OR -> childProgress.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            };
        }
        LeafCondition leaf = (LeafCondition) condition;
        double actual = actualValue(leaf, facts);
        if (leaf.threshold() <= 0) {
            return 100.0;
        }
        return Math.min(100.0, (actual / leaf.threshold()) * 100.0);
    }

    private static double actualValue(LeafCondition leaf, AchievementFactSheet facts) {
        return switch (leaf.type()) {
            case MIN_ENGAGEMENTS_COMPLETED -> facts.engagementsCompleted();
            case MIN_ENGAGEMENTS_WON -> facts.engagementsWon();
            case MIN_BEST_OVERALL_SCORE -> facts.bestOverallScore();
            case MIN_AVERAGE_OVERALL_SCORE -> facts.averageOverallScore();
            case MIN_COMPETENCY_SCORE -> facts.bestCompetencyScores().getOrDefault(leaf.competencyName(), 0);
            case MIN_DISTINCT_SCENARIOS_COMPLETED -> facts.distinctScenariosCompleted();
            case MIN_WIN_RATE_PERCENT -> facts.winRatePercent();
        };
    }
}
