package com.ibm.consulting.sim.lead.application;

import java.util.List;
import java.util.Map;

/**
 * A single, immutable projection for the research workspace. The client opens
 * one deck request and switches documents locally instead of serially loading
 * a separate category after every click.
 */
public record ResearchSourceDeckResponse(Map<String, List<ResearchArtifactResponse>> sourcesByType,
                                         boolean enrichmentPending) {
    public ResearchSourceDeckResponse {
        sourcesByType = sourcesByType == null ? Map.of() : Map.copyOf(sourcesByType);
    }

    /** Compatibility response for callers that do not need asynchronous enrichment state. */
    public ResearchSourceDeckResponse(Map<String, List<ResearchArtifactResponse>> sourcesByType) {
        this(sourcesByType, false);
    }
}
