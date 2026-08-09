package com.ibm.consulting.sim.proposal.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.domain.AiResponseParser;
import com.ibm.consulting.sim.ai.domain.AiValidationException;

import java.util.ArrayList;
import java.util.List;

public class ProposalReviewParser implements AiResponseParser<ProposalReviewNarrative> {
    private final ObjectMapper mapper;
    public ProposalReviewParser(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public ProposalReviewNarrative parse(String rawJson) throws AiValidationException {
        try {
            JsonNode root = mapper.readTree(rawJson);
            String feedback = root.path("executiveFeedback").asText("").trim();
            List<String> actions = new ArrayList<>();
            root.path("improvementActions").forEach(item -> {
                String value = item.asText("").trim();
                if (!value.isBlank()) actions.add(value);
            });
            if (feedback.isBlank() || actions.isEmpty()) throw new AiValidationException("Invalid proposal review response");
            return new ProposalReviewNarrative(feedback, List.copyOf(actions));
        } catch (AiValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiValidationException("Response is not valid proposal review JSON", exception);
        }
    }
}
