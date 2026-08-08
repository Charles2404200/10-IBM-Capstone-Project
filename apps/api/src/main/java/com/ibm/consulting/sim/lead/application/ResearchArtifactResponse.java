package com.ibm.consulting.sim.lead.application;

import java.time.LocalDate;
import java.util.List;

public record ResearchArtifactResponse(
        String id,
        String title,
        String sourceType,
        String summary,
        String evidenceType,
        String confidence,
        String origin,
        LocalDate publishedOn,
        List<String> allowedFactKeys,
        List<String> correlatesWithEvidence,
        String relevanceRationale) {
}
