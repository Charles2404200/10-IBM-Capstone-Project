package com.ibm.consulting.sim.scenario.application;

import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import jakarta.validation.constraints.NotNull;

/** Full profile command kept separate from the learner-facing scenario summary. */
public record UpdateDifficultyProfileRequest(@NotNull DifficultyProfile profile) {}
