package com.ibm.consulting.sim.scenario.application;

import com.ibm.consulting.sim.scenario.domain.ScenarioAuthoringConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record UpdateScenarioAuthoringConfigRequest(@NotNull @Valid ScenarioAuthoringConfig config) {}
