package com.ibm.consulting.sim.lead.application;

import com.ibm.consulting.sim.ai.domain.AiValidationException;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClientIntelligenceFactGuard {

    private ClientIntelligenceFactGuard() {}

    public static void validate(List<ResearchArtifactResponse> artifacts, Map<String, String> allowedFacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            throw new AiValidationException("Client intelligence response contained no artifacts");
        }
        Set<String> allowedIds = allowedFacts.keySet();
        for (ResearchArtifactResponse artifact : artifacts) {
            if (blank(artifact.title()) || blank(artifact.summary()) || blank(artifact.sourceType())) {
                throw new AiValidationException("Client intelligence artifact has missing required text");
            }
            if (artifact.allowedFactKeys() == null || artifact.allowedFactKeys().isEmpty()) {
                throw new AiValidationException("Client intelligence artifact must cite supportedFactIds");
            }
            for (String factId : artifact.allowedFactKeys()) {
                if (!allowedIds.contains(factId)) {
                    throw new AiValidationException("Unsupported fact id emitted by AI: " + factId);
                }
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
