package com.ibm.consulting.sim.proposal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.domain.AiResponseParser;
import com.ibm.consulting.sim.ai.domain.AiValidationException;

public class ProposalDecisionExplanationParser implements AiResponseParser<ProposalDecisionExplanationResponse> {
    private final ObjectMapper mapper;
    public ProposalDecisionExplanationParser(ObjectMapper mapper) { this.mapper = mapper; }
    @Override public ProposalDecisionExplanationResponse parse(String rawJson) throws AiValidationException {
        try {
            String message = mapper.readTree(rawJson).path("message").asText("").trim();
            if (message.isBlank()) throw new AiValidationException("Missing decision explanation");
            return new ProposalDecisionExplanationResponse(message);
        } catch (AiValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiValidationException("Response is not valid decision explanation JSON", exception);
        }
    }
}
