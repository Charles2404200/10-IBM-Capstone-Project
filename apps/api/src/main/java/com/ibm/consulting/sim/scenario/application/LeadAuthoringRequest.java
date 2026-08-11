package com.ibm.consulting.sim.scenario.application;

import com.ibm.consulting.sim.lead.domain.LeadDifficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Draft-only canonical lead definition. Hidden fields are AI ground truth, never learner API data. */
public record LeadAuthoringRequest(
        @NotBlank String companyName,
        @NotBlank String industry,
        String publicDescription,
        @NotNull LeadDifficulty difficulty,
        String potentialValueRange,
        String decisionMaker,
        String technologyStack,
        String budgetSignal,
        String painSeverity,
        @Size(max = 8) List<@Valid Signal> signals) {
    public record Signal(@NotBlank String label, @NotBlank String category) {}
}
