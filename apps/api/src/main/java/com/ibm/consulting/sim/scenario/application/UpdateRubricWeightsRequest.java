package com.ibm.consulting.sim.scenario.application;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** Request payload for setting per-competency rubric weights on a scenario. Weights must sum to 100. */
public record UpdateRubricWeightsRequest(
        @NotEmpty @Size(max = 100) Map<@NotBlank String, @NotNull @Min(0) @Max(100) Integer> weights) {

    @AssertTrue(message = "rubric weights must sum to exactly 100")
    @JsonIgnore
    public boolean isTotalValid() {
        // this method probably meant to validate the total
        if (weights == null || weights.isEmpty() || weights.values().stream().anyMatch(java.util.Objects::isNull)) {
            return true;
        }
        return weights.values().stream().mapToLong(Integer::longValue).sum() == 100;
    }
}
