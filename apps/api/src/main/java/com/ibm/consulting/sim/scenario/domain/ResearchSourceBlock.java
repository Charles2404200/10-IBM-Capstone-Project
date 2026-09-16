package com.ibm.consulting.sim.scenario.domain;

import java.util.List;

/** A selectable, provenance-preserving fragment inside a research source. */
public record ResearchSourceBlock(String id, ResearchSourceBlockType type, String content, String attribution,
                                  List<String> factIds, List<String> corpusChunkIds, Boolean selectable,
                                  ResearchSourceBlockPurpose purpose) {
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
        factIds = factIds == null ? List.of() : List.copyOf(factIds.stream().filter(value -> value != null && !value.isBlank()).toList());
        corpusChunkIds = corpusChunkIds == null ? List.of() : List.copyOf(corpusChunkIds.stream()
            .filter(value -> value != null && !value.isBlank()).toList());
        purpose = purpose == null ? ResearchSourceBlockPurpose.FACT : purpose;
        selectable = selectable == null ? purpose == ResearchSourceBlockPurpose.FACT || purpose == ResearchSourceBlockPurpose.INTERPRETATION : selectable;
        if (selectable && factIds.isEmpty()) {
            throw new InvalidScenarioAuthoringConfigException("Selectable research source block requires fact ids");
        }
    }

    /** Keeps existing scenario authoring JSON and call sites backwards-compatible. */
    public ResearchSourceBlock(String id, ResearchSourceBlockType type, String content, String attribution,
                               List<String> factIds, Boolean selectable, ResearchSourceBlockPurpose purpose) {
        this(id, type, content, attribution, factIds, List.of(), selectable, purpose);
    }

    /** Keeps existing scenario authoring JSON and call sites backwards-compatible. */
    public ResearchSourceBlock(String id, ResearchSourceBlockType type, String content, String attribution) {
        this(id, type, content, attribution, List.of("scenario_source"), List.of(), true, ResearchSourceBlockPurpose.FACT);
    }
}
