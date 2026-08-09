package com.ibm.consulting.sim.meeting.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.domain.AiResponseParser;
import com.ibm.consulting.sim.ai.domain.AiValidationException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Strictly validates the bounded response-choice schema returned by the AI gateway. */
final class GuidedResponseOptionsParser implements AiResponseParser<GuidedResponseOptions> {

    private static final int OPTION_COUNT = 3;
    private final ObjectMapper objectMapper;

    GuidedResponseOptionsParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public GuidedResponseOptions parse(String rawJson) throws AiValidationException {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode optionsNode = root.path("options");
            if (!optionsNode.isArray() || optionsNode.size() != OPTION_COUNT) {
                throw new AiValidationException("Guided responses must contain exactly three options");
            }
            List<String> options = new ArrayList<>();
            Set<String> uniqueOptions = new HashSet<>();
            for (JsonNode optionNode : optionsNode) {
                if (!optionNode.isTextual()) {
                    throw new AiValidationException("Guided response option must be text");
                }
                String option = optionNode.asText().trim();
                if (option.length() < 20 || option.length() > 900
                        || !uniqueOptions.add(option.toLowerCase(Locale.ROOT))) {
                    throw new AiValidationException("Guided response options must be distinct and appropriately sized");
                }
                options.add(option);
            }
            return new GuidedResponseOptions(options);
        } catch (AiValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiValidationException("Guided response options are not valid JSON", exception);
        }
    }
}
