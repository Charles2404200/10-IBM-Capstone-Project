package com.ibm.consulting.sim.achievement.domain;

/** The kind of measurable fact an {@link AchievementCondition} checks against. */
public enum ConditionType {
    /** Total number of engagements the learner has completed (won or lost). */
    MIN_ENGAGEMENTS_COMPLETED,
    /** Total number of engagements with an accepted proposal outcome. */
    MIN_ENGAGEMENTS_WON,
    /** Best overall assessment score ever achieved across all engagements. */
    MIN_BEST_OVERALL_SCORE,
    /** Average overall assessment score across all completed engagements. */
    MIN_AVERAGE_OVERALL_SCORE,
    /** Best score ever achieved for a single named competency (requires {@code competencyName}). */
    MIN_COMPETENCY_SCORE,
    /** Number of distinct scenarios the learner has completed at least once. */
    MIN_DISTINCT_SCENARIOS_COMPLETED,
    /** Win rate across completed engagements, expressed as a whole percentage (0-100). */
    MIN_WIN_RATE_PERCENT
}
