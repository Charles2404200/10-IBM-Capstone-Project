package com.ibm.consulting.sim.scenario.domain;

import com.ibm.consulting.sim.lead.domain.EvidenceType;

import java.util.Set;

/** Deterministic condition for revealing an intelligence field to a learner. */
public record RevealRule(RevealTarget target, Set<EvidenceType> requiredEvidenceTypes, int minimumEvidenceCount) {
    public RevealRule {
        if (target == null) throw new InvalidScenarioAuthoringConfigException("Reveal target is required");
        requiredEvidenceTypes = requiredEvidenceTypes == null ? Set.of() : Set.copyOf(requiredEvidenceTypes);
        if (requiredEvidenceTypes.isEmpty()) {
            throw new InvalidScenarioAuthoringConfigException("A reveal rule requires at least one evidence type");
        }
        if (minimumEvidenceCount < 1 || minimumEvidenceCount > 8) {
            throw new InvalidScenarioAuthoringConfigException("Reveal rule evidence count must be between 1 and 8");
        }
    }
}
