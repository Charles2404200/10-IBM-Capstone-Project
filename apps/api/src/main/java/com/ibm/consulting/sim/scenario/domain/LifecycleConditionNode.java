package com.ibm.consulting.sim.scenario.domain;

import java.util.List;

/** Immutable JSON-safe condition tree. Shape and bounds are enforced by the lifecycle validator. */
public record LifecycleConditionNode(
        Kind kind,
        Operator operator,
        List<LifecycleConditionNode> children,
        LifecycleConditionType conditionType,
        Double threshold,
        String value) {

    public enum Kind { GROUP, LEAF }
    public enum Operator { AND, OR }

    public LifecycleConditionNode {
        children = children == null ? null : List.copyOf(children);
    }

    public static LifecycleConditionNode group(Operator operator, List<LifecycleConditionNode> children) {
        return new LifecycleConditionNode(Kind.GROUP, operator, children, null, null, null);
    }

    public static LifecycleConditionNode leaf(LifecycleConditionType conditionType) {
        return new LifecycleConditionNode(Kind.LEAF, null, null, conditionType, null, null);
    }

    public static LifecycleConditionNode leaf(LifecycleConditionType conditionType, double threshold) {
        return new LifecycleConditionNode(Kind.LEAF, null, null, conditionType, threshold, null);
    }
}
