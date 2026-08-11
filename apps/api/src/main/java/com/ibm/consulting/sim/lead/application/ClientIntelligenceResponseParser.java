package com.ibm.consulting.sim.lead.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.domain.AiResponseParser;
import com.ibm.consulting.sim.ai.domain.AiValidationException;
import com.ibm.consulting.sim.lead.domain.ConfidenceLevel;
import com.ibm.consulting.sim.lead.domain.EvidenceOrigin;
import com.ibm.consulting.sim.lead.domain.EvidenceType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClientIntelligenceResponseParser implements AiResponseParser<List<ResearchArtifactResponse>> {

    private final ObjectMapper objectMapper;
    private final Map<String, String> allowedFacts;
    private final EvidenceType expectedType;

    public ClientIntelligenceResponseParser(ObjectMapper objectMapper, Map<String, String> allowedFacts,
                                            EvidenceType expectedType) {
        this.objectMapper = objectMapper;
        this.allowedFacts = allowedFacts;
        this.expectedType = expectedType;
    }

    @Override
    public List<ResearchArtifactResponse> parse(String rawJson) throws AiValidationException {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
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
                artifacts.add(new ResearchArtifactResponse(
                        textOrDefault(node, "id", "ai-artifact-" + index++),
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
                        "AI-synthesized from canonical fact ids: " + String.join(", ", factIds)));
            }
            ClientIntelligenceFactGuard.validate(artifacts, allowedFacts);
            return artifacts;
        } catch (AiValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new AiValidationException("Malformed client intelligence JSON", e);
        }
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
