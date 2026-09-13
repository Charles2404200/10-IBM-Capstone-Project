package com.ibm.consulting.sim.lead.application;

import com.ibm.consulting.sim.ai.domain.AiValidationException;
import com.ibm.consulting.sim.scenario.domain.ResearchSourceBlock;
import com.ibm.consulting.sim.scenario.domain.ResearchSourceBlockPurpose;

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
            List<ResearchSourceBlock> blocks = artifact.blocks();
            if (blocks == null || blocks.size() < 5) {
                throw new AiValidationException("Client intelligence document must contain at least five readable blocks");
            }
            int characterCount = blocks.stream().mapToInt(block -> block.content().length()).sum();
            if (characterCount < 450) {
                throw new AiValidationException("Client intelligence document is too short for evidence review");
            }
            for (ResearchSourceBlock block : blocks) {
                if (Boolean.TRUE.equals(block.selectable())
                        && block.purpose() != ResearchSourceBlockPurpose.FACT
                        && block.purpose() != ResearchSourceBlockPurpose.INTERPRETATION) {
                    throw new AiValidationException("Only fact or interpretation blocks may be selectable");
                }
                if (Boolean.TRUE.equals(block.selectable()) && block.factIds().isEmpty()) {
                    throw new AiValidationException("Selectable source block must cite fact ids");
                }
                if (Boolean.TRUE.equals(block.selectable()) && guidanceLanguage(block.content())) {
                    throw new AiValidationException("Consulting guidance cannot be emitted as selectable evidence");
                }
                for (String factId : block.factIds()) {
                    if (!allowedIds.contains(factId)) {
                        throw new AiValidationException("Unsupported source block fact id emitted by AI: " + factId);
                    }
                }
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean guidanceLanguage(String value) {
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("consulting team")
                || normalized.contains("consultant should")
                || normalized.contains("use discovery")
                || normalized.contains("use the first conversation")
                || normalized.contains("useful discovery question")
                || normalized.contains("treat this source")
                || normalized.contains("learner should");
    }
}
