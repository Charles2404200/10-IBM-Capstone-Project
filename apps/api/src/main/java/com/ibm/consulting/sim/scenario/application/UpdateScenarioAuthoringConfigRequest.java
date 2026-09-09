package com.ibm.consulting.sim.scenario.application;

import com.ibm.consulting.sim.lead.domain.EvidenceType;
import com.ibm.consulting.sim.scenario.domain.CanonicalFact;
import com.ibm.consulting.sim.scenario.domain.RevealRule;
import com.ibm.consulting.sim.scenario.domain.RevealTarget;
import com.ibm.consulting.sim.scenario.domain.ScenarioAuthoringConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

/** Validated authoring transport contract mapped explicitly into trusted domain values. */
public record UpdateScenarioAuthoringConfigRequest(@NotNull @Valid Config config) {

    public ScenarioAuthoringConfig toDomain() {
        return new ScenarioAuthoringConfig(
                config.canonicalFacts().stream().map(CanonicalFactRequest::toDomain).toList(),
                config.revealRules().stream().map(RevealRuleRequest::toDomain).toList());
    }

    public record Config(
            @NotNull @Size(max = 100) List<@NotNull @Valid CanonicalFactRequest> canonicalFacts,
            @NotNull @Size(max = 10) List<@NotNull @Valid RevealRuleRequest> revealRules) {}

    public record CanonicalFactRequest(
            @NotBlank @Size(max = 100) String id,
            @NotBlank @Size(max = 200) String label,
            @NotBlank @Size(max = 5_000) String value,
            @NotNull EvidenceType evidenceType,
            @NotNull Boolean availableInResearch) {

        CanonicalFact toDomain() {
            return new CanonicalFact(id, label, value, evidenceType, availableInResearch);
        }
    }

    public record RevealRuleRequest(
            @NotNull RevealTarget target,
            @NotEmpty @Size(max = 8) Set<@NotNull EvidenceType> requiredEvidenceTypes,
            @NotNull @Min(1) @Max(8) Integer minimumEvidenceCount) {

        RevealRule toDomain() {
            return new RevealRule(target, requiredEvidenceTypes, minimumEvidenceCount);
        }
    }
}
