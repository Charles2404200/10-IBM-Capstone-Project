package com.ibm.consulting.sim.lead.application;

import com.ibm.consulting.sim.ai.domain.AiValidationException;
import com.ibm.consulting.sim.scenario.domain.ResearchSourceBlock;
import com.ibm.consulting.sim.scenario.domain.ResearchSourceBlockPurpose;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClientIntelligenceFactGuard {

    private ClientIntelligenceFactGuard() {}

    public static void validate(List<ResearchArtifactResponse> artifacts, Map<String, String> allowedFacts) {
        validate(artifacts, allowedFacts, ResearchDocumentPolicy.compact());
    }

    public static void validate(List<ResearchArtifactResponse> artifacts, Map<String, String> allowedFacts,
                                ResearchDocumentPolicy documentPolicy) {
        if (artifacts == null || artifacts.isEmpty()) {
            throw new AiValidationException("Client intelligence response contained no artifacts");
        }
        Set<String> allowedIds = allowedFacts.keySet();
        for (ResearchArtifactResponse artifact : artifacts) {
            if (blank(artifact.title()) || blank(artifact.summary()) || blank(artifact.sourceType())) {
                throw new AiValidationException("Client intelligence artifact has missing required text");
            }
            if (wordCount(artifact.summary()) > 28) {
                throw new AiValidationException("Client intelligence document preview must be concise and cannot recap the evidence");
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
            if (blocks == null || blocks.size() < documentPolicy.minimumBlocks()
                    || blocks.size() > documentPolicy.maximumBlocks()) {
                throw new AiValidationException("Client intelligence document must contain a compact set of distinct evidence blocks");
            }
            long factBlocks = blocks.stream().filter(block -> block.purpose() == ResearchSourceBlockPurpose.FACT).count();
            if (factBlocks != blocks.size()) {
                throw new AiValidationException("Client intelligence document may only contain factual evidence blocks");
            }
            if (factBlocks < documentPolicy.minimumBlocks()) {
                throw new AiValidationException("Client intelligence document must contain enough factual evidence blocks");
            }
            Set<String> uniqueBlockContent = new HashSet<>();
            Set<String> citedCorpusPassages = new HashSet<>();
            for (ResearchSourceBlock block : blocks) {
                if (!Boolean.TRUE.equals(block.selectable())) {
                    throw new AiValidationException("Every research source block must be selectable factual evidence");
                }
                if (block.factIds().isEmpty()) {
                    throw new AiValidationException("Selectable source block must cite fact ids");
                }
                if (guidanceLanguage(block.content())) {
                    throw new AiValidationException("Consulting guidance cannot be emitted as selectable evidence");
                }
                if (!uniqueBlockContent.add(normalize(block.content()))) {
                    throw new AiValidationException("Client intelligence document contains a repeated evidence block");
                }
                if (documentPolicy.equals(ResearchDocumentPolicy.corpusBacked())) {
                    if (block.corpusChunkIds().isEmpty()) {
                        throw new AiValidationException("Corpus-backed evidence block must cite its source passages");
                    }
                    citedCorpusPassages.addAll(block.corpusChunkIds());
                }
                for (String factId : block.factIds()) {
                    if (!allowedIds.contains(factId)) {
                        throw new AiValidationException("Unsupported source block fact id emitted by AI: " + factId);
                    }
                }
            }
            int wordCount = blocks.stream().mapToInt(block -> wordCount(block.content())).sum();
            if (wordCount < documentPolicy.minimumWords() || wordCount > documentPolicy.maximumWords()) {
                throw new AiValidationException("Client intelligence document does not meet its required word count");
            }
            if (documentPolicy.equals(ResearchDocumentPolicy.corpusBacked())
                    && citedCorpusPassages.size() < documentPolicy.minimumBlocks()) {
                throw new AiValidationException("Corpus-backed source must cover every required source passage");
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static int wordCount(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? 0 : normalized.split(" ").length;
    }

    private static boolean guidanceLanguage(String value) {
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("consulting team")
                || normalized.contains("consultant should")
                || normalized.contains("use discovery")
                || normalized.contains("use the first conversation")
                || normalized.contains("useful discovery question")
                || normalized.contains("treat this source")
                || normalized.contains("learner should")
                || normalized.contains("for this research angle")
                || normalized.contains("briefing develops the reported point")
                || normalized.contains("carries the reported point")
                || normalized.contains("reported point remains central");
    }
}
