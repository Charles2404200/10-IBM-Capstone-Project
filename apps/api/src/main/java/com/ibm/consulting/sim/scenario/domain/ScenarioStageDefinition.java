package com.ibm.consulting.sim.scenario.domain;

/** Author-owned presentation and additional gates for one supported technical lifecycle capability. */
public record ScenarioStageDefinition(
        String key,
        StageCapability capability,
        String label,
        String description,
        String goal,
        String doneText,
        String nextText,
        boolean required,
        int displayOrder,
        LifecycleConditionNode entryCondition,
        LifecycleConditionNode completionCondition) {
}
