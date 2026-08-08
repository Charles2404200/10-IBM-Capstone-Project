package com.ibm.consulting.sim.ai.domain;

import java.util.Set;

/**
 * Declares what a given {@link AiProvider} is good for, so the router can make
 * capability-based decisions ("this task needs LOW latency + structured
 * output") instead of hard-coding vendor names into business logic.
 */
public record ProviderCapabilities(
        Set<AiTaskType> supportedTasks,
        LatencyTier latency,
        ReasoningTier reasoning,
        boolean structuredOutput) {

    public boolean supports(AiTaskType task) {
        return supportedTasks.contains(task);
    }
}
