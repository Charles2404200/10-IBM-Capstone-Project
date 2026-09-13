package com.ibm.consulting.sim.scenario.domain;

/** A selectable, provenance-preserving fragment inside a research source. */
public record ResearchSourceBlock(String id, ResearchSourceBlockType type, String content, String attribution) {
    public ResearchSourceBlock {
        if (id == null || id.isBlank()) {
            throw new InvalidScenarioAuthoringConfigException("Research source block id is required");
        }
        if (type == null) {
            throw new InvalidScenarioAuthoringConfigException("Research source block type is required");
        }
        if (content == null || content.isBlank()) {
            throw new InvalidScenarioAuthoringConfigException("Research source block content is required");
        }
        id = id.trim();
        content = content.trim();
        attribution = attribution == null || attribution.isBlank() ? null : attribution.trim();
    }
}
