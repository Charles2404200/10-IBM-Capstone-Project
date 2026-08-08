package com.ibm.consulting.sim.achievement.domain;

import java.util.List;

/**
 * A group of child conditions combined with {@link LogicalOperator#AND} or
 * {@link LogicalOperator#OR}. Groups may nest arbitrarily deep, allowing an admin
 * to compose rules such as "(won >= 3 AND avgScore >= 70) OR (won >= 10)".
 */
public record ConditionGroup(LogicalOperator operator, List<AchievementCondition> children)
        implements AchievementCondition {

    public ConditionGroup {
        if (children == null || children.isEmpty()) {
            throw new IllegalArgumentException("A condition group must have at least one child condition");
        }
    }
}
