package com.ibm.consulting.sim.scenario.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.scenario.domain.InvalidScenarioLifecycleDefinitionException;
import com.ibm.consulting.sim.scenario.domain.ScenarioLifecycleDefinition;
import com.ibm.consulting.sim.scenario.domain.ScenarioLifecycleDefinitionValidator;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/** Strict bounded JSON codec for validated scenario lifecycle definitions. */
@Component
public final class LifecycleDefinitionCodec {

    public static final int MAX_JSON_LENGTH = 100_000;

    private final ObjectMapper objectMapper;
    private final ScenarioLifecycleDefinitionValidator validator;

    @Autowired
    public LifecycleDefinitionCodec(ObjectMapper objectMapper) {
        this(objectMapper, new ScenarioLifecycleDefinitionValidator());
    }

    public LifecycleDefinitionCodec(
            ObjectMapper objectMapper, ScenarioLifecycleDefinitionValidator validator) {
        if (objectMapper == null) throw new IllegalArgumentException("ObjectMapper is required");
        if (validator == null) throw new IllegalArgumentException("Lifecycle validator is required");
        this.objectMapper = objectMapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.validator = validator;
    }

    /** Decodes stored JSON; only a database null resolves to the legacy-safe defaults. */
    public ScenarioLifecycleDefinition decode(String json) {
        return decode(json, null);
    }

    /** Decodes stored JSON, mapping a legacy briefing objective only when lifecycle JSON is null. */
    public ScenarioLifecycleDefinition decode(String json, String legacyObjective) {
        if (json == null) return ScenarioLifecycleDefinition.defaults(legacyObjective);
        if (json.length() > MAX_JSON_LENGTH) {
            throw new InvalidScenarioLifecycleDefinitionException("Scenario lifecycle JSON is too large");
        }
        if (json.isBlank()) {
            throw new InvalidScenarioLifecycleDefinitionException("Scenario lifecycle JSON must not be blank");
        }
        try {
            ScenarioLifecycleDefinition definition = objectMapper.readValue(json, ScenarioLifecycleDefinition.class);
            validator.validate(definition);
            return definition;
        } catch (InvalidScenarioLifecycleDefinitionException exception) {
            throw exception;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InvalidScenarioLifecycleDefinitionException("Scenario lifecycle configuration is not valid JSON",
                    exception);
        }
    }

    /** Validates before serializing and refuses output beyond the storage boundary. */
    public String encode(ScenarioLifecycleDefinition definition) {
        validator.validate(definition);
        try {
            String encoded = objectMapper.writeValueAsString(definition);
            if (encoded.length() > MAX_JSON_LENGTH) {
                throw new InvalidScenarioLifecycleDefinitionException("Scenario lifecycle JSON is too large");
            }
            return encoded;
        } catch (JsonProcessingException exception) {
            throw new InvalidScenarioLifecycleDefinitionException("Scenario lifecycle configuration cannot be saved",
                    exception);
        }
    }
}
