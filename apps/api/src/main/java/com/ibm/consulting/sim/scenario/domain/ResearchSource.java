package com.ibm.consulting.sim.scenario.domain;

import com.ibm.consulting.sim.lead.domain.ConfidenceLevel;
import com.ibm.consulting.sim.lead.domain.EvidenceType;

import java.util.List;

/** A versioned source card that a learner must assess before using as evidence. */
public record ResearchSource(String id, String title, String sourceType, String summary, EvidenceType evidenceType,
                             ConfidenceLevel confidence, int relevanceScore, List<ResearchSourceBlock> blocks) {
    public ResearchSource {
        id = required(id, "Research source id");
        title = required(title, "Research source title");
        sourceType = required(sourceType, "Research source type");
        summary = required(summary, "Research source summary");
        if (evidenceType == null) throw new InvalidScenarioAuthoringConfigException("Research source category is required");
        if (confidence == null) throw new InvalidScenarioAuthoringConfigException("Research source reliability is required");
        if (relevanceScore < 0 || relevanceScore > 100) {
            throw new InvalidScenarioAuthoringConfigException("Research source relevance must be between 0 and 100");
        }
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        long distinctBlocks = blocks.stream().map(ResearchSourceBlock::id).distinct().count();
        if (distinctBlocks != blocks.size()) {
            throw new InvalidScenarioAuthoringConfigException("Research source block ids must be unique within a source");
        }
    }

    /** Keeps configurations saved before document blocks were introduced valid. */
    public ResearchSource(String id, String title, String sourceType, String summary, EvidenceType evidenceType,
                          ConfidenceLevel confidence, int relevanceScore) {
        this(id, title, sourceType, summary, evidenceType, confidence, relevanceScore, List.of());
    }

    public List<ResearchSourceBlock> effectiveBlocks() {
        return blocks.isEmpty()
                ? List.of(new ResearchSourceBlock("summary", ResearchSourceBlockType.PARAGRAPH, summary, null))
                : blocks;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new InvalidScenarioAuthoringConfigException(label + " is required");
        return value.trim();
    }
}
