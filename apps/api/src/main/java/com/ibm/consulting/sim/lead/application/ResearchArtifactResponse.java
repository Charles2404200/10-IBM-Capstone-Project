package com.ibm.consulting.sim.lead.application;

import java.time.LocalDate;
import java.util.List;
import com.ibm.consulting.sim.scenario.domain.ResearchSourceBlock;

public record ResearchArtifactResponse(
        String id,
        String title,
        String sourceType,
        String summary,
        String evidenceType,
        String confidence,
        String origin,
        LocalDate publishedOn,
        int relevanceScore,
        List<String> allowedFactKeys,
        List<String> correlatesWithEvidence,
        String relevanceRationale,
        List<ResearchSourceBlock> blocks) {
    public ResearchArtifactResponse {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    /** Compatibility constructor for existing projection and AI-parser call sites. */
    public ResearchArtifactResponse(String id, String title, String sourceType, String summary, String evidenceType,
                                    String confidence, String origin, LocalDate publishedOn, int relevanceScore,
                                    List<String> allowedFactKeys, List<String> correlatesWithEvidence,
                                    String relevanceRationale) {
        this(id, title, sourceType, summary, evidenceType, confidence, origin, publishedOn, relevanceScore,
                allowedFactKeys, correlatesWithEvidence, relevanceRationale, List.of());
    }
}
