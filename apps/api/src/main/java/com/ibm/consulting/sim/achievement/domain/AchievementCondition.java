package com.ibm.consulting.sim.achievement.domain;

/**
 * A composable achievement unlock rule. Either a boolean {@link LogicalOperator}
 * combination of child rules ({@link ConditionGroup}) or a single measurable
 * threshold check ({@link LeafCondition}). Pure domain model — no persistence or
 * serialisation concerns leak in here; that is handled by the infrastructure layer.
 */
public sealed interface AchievementCondition permits ConditionGroup, LeafCondition {
}
