package com.ibm.consulting.sim.ai.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.domain.AiResponseParser;
import com.ibm.consulting.sim.ai.domain.AiValidationException;
import com.ibm.consulting.sim.ai.domain.PersonaStateDelta;
import com.ibm.consulting.sim.ai.domain.PersonaTurnResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Parses and schema-validates the raw JSON produced by the persona-dialogue
 * use case (§5.4). Rejects unknown fact identifiers so the AI cannot invent
 * disclosures that the scenario never authorised.
 */
public class PersonaTurnResponseParser implements AiResponseParser<PersonaTurnResponse> {

    private final ObjectMapper mapper;
    private final Set<String> knownFactIds;

    public PersonaTurnResponseParser(ObjectMapper mapper, Set<String> knownFactIds) {
        this.mapper = mapper;
        this.knownFactIds = knownFactIds;
    }

    @Override
    public PersonaTurnResponse parse(String rawJson) throws AiValidationException {
        JsonNode root;
        try {
            root = mapper.readTree(rawJson);
        } catch (Exception e) {
            throw new AiValidationException("Response is not valid JSON", e);
        }

        String spokenResponse = textOrThrow(root, "spokenResponse");
        JsonNode deltaNode = root.path("stateDelta");
        if (deltaNode.isMissingNode() || deltaNode.isNull()) {
            throw new AiValidationException("Missing required field: stateDelta");
        }

        PersonaStateDelta delta = new PersonaStateDelta(
                deltaNode.path("trust").asInt(0),
                deltaNode.path("interest").asInt(0),
                deltaNode.path("patience").asInt(0)).clamped();

        List<String> factsDisclosed = new ArrayList<>();
        for (JsonNode fact : root.path("factsDisclosed")) {
            String factId = fact.asText();
            if (!knownFactIds.isEmpty() && !knownFactIds.contains(factId)) {
                throw new AiValidationException("Unknown fact identifier disclosed: " + factId);
            }
            factsDisclosed.add(factId);
        }

        List<String> behaviours = new ArrayList<>();
        root.path("detectedLearnerBehaviours").forEach(n -> behaviours.add(n.asText()));

        List<String> signals = new ArrayList<>();
        root.path("meetingSignals").forEach(n -> signals.add(n.asText()));

        List<String> guidedResponseOptions = new ArrayList<>();
        root.path("guidedResponseOptions").forEach(n -> guidedResponseOptions.add(n.asText()));

        JsonNode safetyNode = root.path("safety");
        boolean allowed = safetyNode.path("allowed").asBoolean(true);
        String reason = safetyNode.hasNonNull("reason") ? safetyNode.get("reason").asText() : null;

        String objection = root.hasNonNull("objectionRaised") ? root.get("objectionRaised").asText() : null;

        return new PersonaTurnResponse(spokenResponse, behaviours, delta, factsDisclosed, objection,
                signals, new PersonaTurnResponse.SafetyCheck(allowed, reason), guidedResponseOptions);
    }

    private String textOrThrow(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || !node.isTextual() || node.asText().isBlank()) {
            throw new AiValidationException("Missing required field: " + field);
        }
        return node.asText();
    }
}
