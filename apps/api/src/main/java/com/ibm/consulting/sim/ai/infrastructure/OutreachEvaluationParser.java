package com.ibm.consulting.sim.ai.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.domain.AiResponseParser;
import com.ibm.consulting.sim.ai.domain.AiValidationException;
import com.ibm.consulting.sim.ai.domain.OutreachEvaluationResult;

public class OutreachEvaluationParser implements AiResponseParser<OutreachEvaluationResult> {

    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;

    private final ObjectMapper mapper;

    public OutreachEvaluationParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public OutreachEvaluationResult parse(String rawJson) throws AiValidationException {
        JsonNode root;
        try {
            root = mapper.readTree(rawJson);
        } catch (Exception e) {
            throw new AiValidationException("Response is not valid JSON", e);
        }

        String clientReply = root.path("clientReply").asText(null);
        String outcome = root.path("outcome").asText(null);
        if (clientReply == null || clientReply.isBlank() || outcome == null) {
            throw new AiValidationException("Missing required fields: clientReply/outcome");
        }
        if (!outcome.equals("ACCEPTED") && !outcome.equals("FOLLOW_UP_REQUIRED") && !outcome.equals("REJECTED")) {
            throw new AiValidationException("Unknown outcome value: " + outcome);
        }

        JsonNode scores = root.path("scores");
        JsonNode delta = root.path("relationshipStateDelta");

        return new OutreachEvaluationResult(
                clientReply,
                outcome,
                clamp(scores.path("personalisation").asInt(50)),
                clamp(scores.path("relevance").asInt(50)),
                clamp(scores.path("clarity").asInt(50)),
                clamp(scores.path("callToAction").asInt(50)),
                delta.path("trust").asInt(0),
                delta.path("interest").asInt(0));
    }

    private int clamp(int value) {
        return Math.max(MIN_SCORE, Math.min(MAX_SCORE, value));
    }
}
