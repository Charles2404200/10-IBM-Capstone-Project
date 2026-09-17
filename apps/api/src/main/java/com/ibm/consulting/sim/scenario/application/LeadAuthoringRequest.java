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
        @Size(max = 2_000) String publicDescription,
        @NotNull LeadDifficulty difficulty,
        @Size(max = 100) String potentialValueRange,
        @Size(max = 150) String decisionMaker,
        @Size(max = 200) String technologyStack,
        @Size(max = 150) String budgetSignal,
        @Size(max = 100) String painSeverity,
        @Size(max = 8) List<@Valid Signal> signals) {
    public record Signal(@NotBlank @Size(max = 300) String label, @NotBlank @Size(max = 100) String category) {}
}
