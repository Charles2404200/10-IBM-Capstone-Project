package com.ibm.consulting.sim.scenario.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.scenario.domain.InvalidScenarioAuthoringConfigException;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioAuthoringConfig;
import org.springframework.stereotype.Service;

/** Codec and resolver for structured scenario truth/reveal configuration. */
@Service
public class ScenarioAuthoringConfigService {
    private final ObjectMapper objectMapper;

    public ScenarioAuthoringConfigService(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public ScenarioAuthoringConfig forScenario(Scenario scenario) {
        String encoded = scenario.getAuthoringConfig();
        if (encoded == null || encoded.isBlank()) return ScenarioAuthoringConfig.defaults();
        try {
            return objectMapper.readValue(encoded, ScenarioAuthoringConfig.class);
        } catch (JsonProcessingException exception) {
            throw new InvalidScenarioAuthoringConfigException("Scenario authoring configuration is not valid", exception);
        }
    }

    public void update(Scenario scenario, ScenarioAuthoringConfig config) {
        try {
            scenario.updateAuthoringConfig(objectMapper.writeValueAsString(config == null ? ScenarioAuthoringConfig.empty() : config));
        } catch (JsonProcessingException exception) {
            throw new InvalidScenarioAuthoringConfigException("Scenario authoring configuration cannot be saved", exception);
        }
    }
}
