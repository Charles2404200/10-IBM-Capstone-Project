package com.ibm.consulting.sim.achievement.domain;

/**
 * A single measurable threshold check, e.g. "at least 3 engagements won" or
 * "Relationship Building competency score of at least 80". {@code competencyName}
 * is only meaningful (and required) for {@link ConditionType#MIN_COMPETENCY_SCORE}.
 */
public record LeafCondition(ConditionType type, String competencyName, double threshold)
        implements AchievementCondition {

    public LeafCondition {
        if (type == ConditionType.MIN_COMPETENCY_SCORE && (competencyName == null || competencyName.isBlank())) {
            throw new IllegalArgumentException("competencyName is required for MIN_COMPETENCY_SCORE conditions");
        }
    }
}
