package com.ibm.consulting.sim.scenario.domain;

/** Immutable scenario objective, optionally nested below another objective and associated with a stage. */
public record ScenarioObjectiveDefinition(
        String key,
        String parentObjectiveKey,
        String stageKey,
        String title,
        String description,
        boolean required,
        int displayOrder,
        LifecycleConditionNode completionCondition) {
}
