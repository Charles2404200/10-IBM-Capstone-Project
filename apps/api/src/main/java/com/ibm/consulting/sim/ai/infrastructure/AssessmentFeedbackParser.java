package com.ibm.consulting.sim.ai.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.domain.AiResponseParser;
import com.ibm.consulting.sim.ai.domain.AiValidationException;
import com.ibm.consulting.sim.ai.domain.AssessmentFeedback;

import java.util.ArrayList;
import java.util.List;

public class AssessmentFeedbackParser implements AiResponseParser<AssessmentFeedback> {

    private final ObjectMapper mapper;

    public AssessmentFeedbackParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AssessmentFeedback parse(String rawJson) throws AiValidationException {
        JsonNode root;
        try {
            root = mapper.readTree(rawJson);
        } catch (Exception e) {
            throw new AiValidationException("Response is not valid JSON", e);
        }

        String summary = root.path("feedbackSummary").asText(null);
        if (summary == null || summary.isBlank()) {
            throw new AiValidationException("Missing required field: feedbackSummary");
        }

        List<String> strengths = new ArrayList<>();
        root.path("strengths").forEach(n -> strengths.add(n.asText()));

        List<String> improvementAreas = new ArrayList<>();
        root.path("improvementAreas").forEach(n -> improvementAreas.add(n.asText()));

        return new AssessmentFeedback(summary, strengths, improvementAreas);
    }
}
