package com.ibm.consulting.sim.scenario.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Request payload for creating a new scenario (draft state). */
public record CreateScenarioRequest(
        @NotBlank String title,
        @NotBlank String industry,
        @NotBlank String description,
        @Min(1) @Max(5) int difficulty) {
}
