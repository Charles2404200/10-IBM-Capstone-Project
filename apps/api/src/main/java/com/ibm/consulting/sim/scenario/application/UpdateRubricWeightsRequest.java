package com.ibm.consulting.sim.scenario.application;

import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

/** Request payload for setting per-competency rubric weights on a scenario. Weights must sum to 100. */
public record UpdateRubricWeightsRequest(@NotEmpty Map<String, Integer> weights) {
}
