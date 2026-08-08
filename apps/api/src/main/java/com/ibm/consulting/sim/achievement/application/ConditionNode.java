package com.ibm.consulting.sim.achievement.application;

import com.ibm.consulting.sim.achievement.domain.ConditionType;
import com.ibm.consulting.sim.achievement.domain.LogicalOperator;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Flat, self-describing representation of an {@link com.ibm.consulting.sim.achievement.domain.AchievementCondition}
 * tree, used both as the admin-facing request/response payload (so an admin UI can
 * render/build the rule visually) and as the on-disk JSON representation persisted
 * on {@link com.ibm.consulting.sim.achievement.domain.Achievement#getRuleJson()}.
 *
 * <p>Exactly one of the two shapes is populated depending on {@code kind}:
 * <ul>
 *   <li>{@code GROUP}: {@code operator} + {@code children} are set, leaf fields are null.</li>
 *   <li>{@code LEAF}: {@code type} + {@code threshold} (+ {@code competencyName} for
 *       {@code MIN_COMPETENCY_SCORE}) are set, group fields are null.</li>
 * </ul>
 */
public record ConditionNode(
        @NotNull Kind kind,
        LogicalOperator operator,
        List<ConditionNode> children,
        ConditionType type,
        String competencyName,
        Double threshold) {

    public enum Kind { GROUP, LEAF }

    public static ConditionNode group(LogicalOperator operator, List<ConditionNode> children) {
        return new ConditionNode(Kind.GROUP, operator, children, null, null, null);
    }

    public static ConditionNode leaf(ConditionType type, String competencyName, double threshold) {
        return new ConditionNode(Kind.LEAF, null, null, type, competencyName, threshold);
    }
}
