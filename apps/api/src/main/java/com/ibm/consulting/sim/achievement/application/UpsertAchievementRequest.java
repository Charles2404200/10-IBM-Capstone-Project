package com.ibm.consulting.sim.achievement.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request payload for creating or updating an achievement definition. */
public record UpsertAchievementRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 2_000) String description,
        @Size(max = 100) String iconKey,
        @NotNull @Valid ConditionNode rule) {
}
