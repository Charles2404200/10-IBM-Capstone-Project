package com.ibm.consulting.sim.scenario.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Draft-only editable scenario metadata and learner briefing. */
public record UpdateScenarioBlueprintRequest(
        @NotBlank String title,
        @NotBlank String industry,
        @NotBlank String description,
        @Min(1) @Max(5) int difficulty,
        String consultantRole,
        String objective,
        List<String> successCriteria,
        @Min(1) @Max(90) int simulatedDays,
        @Min(1) @Max(5) int informationAmbiguity,
        @Min(1) @Max(5) int stakeholderComplexity,
        @Min(1) @Max(5) int commercialPressure) {}
