package com.ibm.consulting.sim.scenario.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioSummaryContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializedSummaryUsesContentVersionAndPreservesOptionalPersonaFields() throws Exception {
        Scenario scenario = Scenario.create("Contract scenario", "Technology", "Description", 3);
        scenario.addPersona("Client", "CIO", "Example Co", null, null,
                "Hidden concern", "Improve delivery");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(ScenarioSummary.from(scenario)));

        assertThat(json.has("contentVersion")).isTrue();
        assertThat(json.has("version")).isFalse();
        assertThat(json.path("contentVersion").asInt()).isEqualTo(1);
        assertThat(json.at("/personas/0/communicationStyle").isNull()).isTrue();
        assertThat(json.at("/personas/0/visibleConcerns").isNull()).isTrue();
        assertThat(json.at("/personas/0").has("hiddenConcerns")).isFalse();
    }
}
