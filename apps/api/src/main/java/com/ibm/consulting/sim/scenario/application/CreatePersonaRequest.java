package com.ibm.consulting.sim.scenario.application;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for adding a persona to a scenario. {@code hiddenConcerns} is
 * writable here (author-only endpoint) even though it is never exposed via the
 * learner-facing read API ({@link ScenarioSummary.PersonaSummary}).
 */
public record CreatePersonaRequest(
        @NotBlank String name,
        @NotBlank String jobTitle,
        @NotBlank String organisation,
        String communicationStyle,
        String visibleConcerns,
        String hiddenConcerns,
        String businessGoals) {
}
