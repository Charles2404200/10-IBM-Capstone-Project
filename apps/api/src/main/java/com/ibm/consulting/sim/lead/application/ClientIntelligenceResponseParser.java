package com.ibm.consulting.sim.lead.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.domain.AiResponseParser;
import com.ibm.consulting.sim.ai.domain.AiValidationException;
import com.ibm.consulting.sim.lead.domain.ConfidenceLevel;
import com.ibm.consulting.sim.lead.domain.EvidenceOrigin;
import com.ibm.consulting.sim.lead.domain.EvidenceType;
import com.ibm.consulting.sim.scenario.domain.ResearchSourceBlock;
import com.ibm.consulting.sim.scenario.domain.ResearchSourceBlockType;
import com.ibm.consulting.sim.scenario.domain.ResearchSourceBlockPurpose;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClientIntelligenceResponseParser implements AiResponseParser<List<ResearchArtifactResponse>> {

    private final ObjectMapper objectMapper;
    private final Map<String, String> allowedFacts;
    private final EvidenceType expectedType;
    private final ResearchDocumentPolicy documentPolicy;
    private final Set<String> approvedCorpusChunkIds;

    public ClientIntelligenceResponseParser(ObjectMapper objectMapper, Map<String, String> allowedFacts,
                                            EvidenceType expectedType) {
        this(objectMapper, allowedFacts, expectedType, ResearchDocumentPolicy.compact());
    }

    public ClientIntelligenceResponseParser(ObjectMapper objectMapper, Map<String, String> allowedFacts,
                                            EvidenceType expectedType, ResearchDocumentPolicy documentPolicy) {
        this(objectMapper, allowedFacts, expectedType, documentPolicy, Set.of());
    }

    public ClientIntelligenceResponseParser(ObjectMapper objectMapper, Map<String, String> allowedFacts,
                                            EvidenceType expectedType, ResearchDocumentPolicy documentPolicy,
                                            Set<String> approvedCorpusChunkIds) {
        this.objectMapper = objectMapper;
        this.allowedFacts = allowedFacts;
        this.expectedType = expectedType;
        this.documentPolicy = documentPolicy;
        this.approvedCorpusChunkIds = Set.copyOf(approvedCorpusChunkIds);
    }

    @Override
    public List<ResearchArtifactResponse> parse(String rawJson) throws AiValidationException {
        try {
            JsonNode root = objectMapper.readTree(jsonPayload(rawJson));
            JsonNode artifactsNode = root.has("artifacts") ? root.get("artifacts") : root;
            if (!artifactsNode.isArray()) {
                artifactsNode = objectMapper.createArrayNode().add(root);
            }

            List<ResearchArtifactResponse> artifacts = new ArrayList<>();
            int index = 1;
            for (JsonNode node : artifactsNode) {
                EvidenceType type = parseEnum(EvidenceType.class, requiredText(node, "category"));
                if (type != expectedType) {
                    throw new AiValidationException("Artifact category " + type + " did not match requested " + expectedType);
                }
                ConfidenceLevel reliability = parseEnum(ConfidenceLevel.class,
                        node.hasNonNull("reliability") ? node.get("reliability").asText() : requiredText(node, "confidence"));
                List<String> factIds = stringList(node.path("supportedFactIds"));
                String artifactId = textOrDefault(node, "id", "ai-artifact-" + index++);
                artifacts.add(new ResearchArtifactResponse(
                        artifactId,
                        requiredText(node, "title"),
                        requiredText(node, "sourceType"),
                        requiredText(node, "content"),
                        type.name(),
                        reliability.name(),
                        EvidenceOrigin.AI_SYNTHESIZED.name(),
                        LocalDate.now(),
                        relevancePercent(node.path("relevance").asDouble(0.8d)),
                        factIds,
                        List.of(),
                        "AI-synthesized from canonical fact ids: " + String.join(", ", factIds),
                        parseBlocks(node.path("blocks"), artifactId, requiredText(node, "content"), factIds)));
            }
            ClientIntelligenceFactGuard.validate(artifacts, allowedFacts, documentPolicy);
            validateCorpusProvenance(artifacts);
            return artifacts;
        } catch (AiValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new AiValidationException("Malformed client intelligence JSON", e);
        }
    }

    private void validateCorpusProvenance(List<ResearchArtifactResponse> artifacts) {
        if (!documentPolicy.equals(ResearchDocumentPolicy.corpusBacked())) {
            return;
        }
        for (ResearchArtifactResponse artifact : artifacts) {
            for (ResearchSourceBlock block : artifact.blocks()) {
                if (!approvedCorpusChunkIds.containsAll(block.corpusChunkIds())) {
                    throw new AiValidationException("Corpus-backed dossier cited a passage outside the approved lane corpus");
                }
            }
        }
    }

    private static String jsonPayload(String rawJson) {
        String trimmed = rawJson == null ? "" : rawJson.trim();
        if (trimmed.startsWith("```")) {
            int contentStart = trimmed.indexOf('\n');
            int closingFence = trimmed.lastIndexOf("```");
            if (contentStart >= 0 && closingFence > contentStart) {
                return trimmed.substring(contentStart + 1, closingFence).trim();
            }
        }
        int objectStart = trimmed.indexOf('{');
        int arrayStart = trimmed.indexOf('[');
        int jsonStart = objectStart < 0 ? arrayStart : arrayStart < 0 ? objectStart : Math.min(objectStart, arrayStart);
        return jsonStart > 0 ? trimmed.substring(jsonStart) : trimmed;
    }

    private static String requiredText(JsonNode node, String field) {
        String value = textOrDefault(node, field, null);
        if (value == null || value.isBlank()) {
            throw new AiValidationException("Missing required field: " + field);
        }
        return value;
    }

    private static String textOrDefault(JsonNode node, String field, String fallback) {
        return node.hasNonNull(field) ? node.get(field).asText() : fallback;
    }

    private static List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return values;
    }

    private static List<ResearchSourceBlock> parseBlocks(JsonNode blocksNode, String artifactId, String summary,
                                                         List<String> artifactFactIds) {
        List<ResearchSourceBlock> blocks = new ArrayList<>();
        if (blocksNode.isArray()) {
            int index = 1;
            for (JsonNode block : blocksNode) {
                String content = textOrDefault(block, "content", null);
                if (content == null || content.isBlank()) {
                    continue;
                }
                ResearchSourceBlockType type = parseEnum(ResearchSourceBlockType.class,
                        textOrDefault(block, "type", ResearchSourceBlockType.PARAGRAPH.name()));
                String id = textOrDefault(block, "id", artifactId + "-block-" + index++);
                if (!block.hasNonNull("purpose") || !block.hasNonNull("selectable") || !block.has("factIds")) {
                    throw new AiValidationException("AI source block must declare purpose, selectable state and fact ids");
                }
                ResearchSourceBlockPurpose purpose = parseEnum(ResearchSourceBlockPurpose.class,
                        block.get("purpose").asText());
                Boolean selectable = block.get("selectable").asBoolean();
                List<String> blockFactIds = stringList(block.path("factIds"));
                List<String> corpusChunkIds = stringList(block.path("corpusChunkIds"));
                blocks.add(new ResearchSourceBlock(id, type, content, textOrDefault(block, "attribution", null),
                    blockFactIds, corpusChunkIds, selectable, purpose));
            }
        }
        if (blocks.isEmpty()) {
            blocks.add(new ResearchSourceBlock(artifactId + "-summary", ResearchSourceBlockType.PARAGRAPH, summary, null,
                    artifactFactIds, false, ResearchSourceBlockPurpose.CONTEXT));
        }
        return List.copyOf(blocks);
    }

    private static int relevancePercent(double relevance) {
        double normalized = relevance <= 1d ? relevance * 100d : relevance;
        return (int) Math.round(Math.max(0d, Math.min(100d, normalized)));
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value);
        } catch (Exception e) {
            throw new AiValidationException("Invalid enum value " + value + " for " + type.getSimpleName());
        }
    }
}
