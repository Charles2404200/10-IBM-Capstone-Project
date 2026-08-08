package com.ibm.consulting.sim.achievement.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request payload for creating or updating an achievement definition. */
public record UpsertAchievementRequest(
        @NotBlank String name,
        String description,
        String iconKey,
        @NotNull @Valid ConditionNode rule) {
}
