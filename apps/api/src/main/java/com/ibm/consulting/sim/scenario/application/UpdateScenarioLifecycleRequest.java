package com.ibm.consulting.sim.scenario.application;

import com.ibm.consulting.sim.scenario.domain.ScenarioLifecycleDefinition;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateScenarioLifecycleRequest(
        @NotNull ScenarioLifecycleDefinition definition,
        @NotNull @PositiveOrZero Long version) {}
