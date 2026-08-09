package com.ibm.consulting.sim.proposal.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.domain.AiResponseParser;
import com.ibm.consulting.sim.ai.domain.AiValidationException;

import java.util.ArrayList;
import java.util.List;

public class ProposalChallengeParser implements AiResponseParser<ProposalChallengeResponse> {
    private final ObjectMapper mapper;
    public ProposalChallengeParser(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public ProposalChallengeResponse parse(String rawJson) throws AiValidationException {
        try {
            JsonNode root = mapper.readTree(rawJson);
            List<String> concerns = new ArrayList<>();
            root.path("concerns").forEach(item -> {
                String value = item.asText("").trim();
                if (!value.isBlank()) concerns.add(value);
            });
            if (concerns.isEmpty()) throw new AiValidationException("Missing proposal concerns");
            return new ProposalChallengeResponse(List.copyOf(concerns));
        } catch (AiValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiValidationException("Response is not valid proposal challenge JSON", exception);
        }
    }
}
