package com.ibm.consulting.sim.scenario.domain;

import com.ibm.consulting.sim.lead.domain.EvidenceType;

/**
 * Scenario-approved truth that may ground research generation. It is authored
 * independently from learner evidence; an LLM can phrase it, never change it.
 */
public record CanonicalFact(String id, String label, String value, EvidenceType evidenceType,
                            boolean availableInResearch) {
    public CanonicalFact {
        id = require(id, "Fact id");
        label = require(label, "Fact label");
        value = require(value, "Fact value");
        if (evidenceType == null) throw new InvalidScenarioAuthoringConfigException("Fact evidence type is required");
    }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new InvalidScenarioAuthoringConfigException(label + " is required");
        }
        return value.trim();
    }
}
