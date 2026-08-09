package com.ibm.consulting.sim.proposal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.domain.AiResponseParser;
import com.ibm.consulting.sim.ai.domain.AiValidationException;

public class ProposalClientDecisionParser implements AiResponseParser<ProposalClientDecision> {
    private final ObjectMapper mapper;
    public ProposalClientDecisionParser(ObjectMapper mapper) { this.mapper = mapper; }
    @Override public ProposalClientDecision parse(String rawJson) throws AiValidationException {
        try {
            String message = mapper.readTree(rawJson).path("message").asText("").trim();
            if (message.isBlank()) throw new AiValidationException("Missing client decision message");
            return new ProposalClientDecision(message);
        } catch (AiValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiValidationException("Response is not valid client decision JSON", exception);
        }
    }
}
