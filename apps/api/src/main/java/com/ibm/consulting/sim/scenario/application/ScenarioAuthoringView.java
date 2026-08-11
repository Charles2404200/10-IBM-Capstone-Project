package com.ibm.consulting.sim.scenario.application;

import com.ibm.consulting.sim.scenario.domain.ScenarioAuthoringConfig;

import java.util.List;
import java.util.UUID;

/** Author-only view. Canonical truth is intentionally absent from learner APIs. */
public record ScenarioAuthoringView(
        ScenarioSummary scenario,
        UUID lineageId,
        ScenarioAuthoringConfig config,
        Readiness readiness) {
    public record Readiness(boolean readyToPublish, List<String> blockers, int personaCount, int leadCount,
                            int canonicalFactCount, int revealRuleCount) {}
}
