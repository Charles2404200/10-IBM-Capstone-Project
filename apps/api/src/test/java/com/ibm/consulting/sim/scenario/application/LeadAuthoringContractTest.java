package com.ibm.consulting.sim.scenario.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadDifficulty;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LeadAuthoringContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void incompleteDraftLeadSerializesNonNullStringsAndAnEmptySignalsArray() throws Exception {
        Lead draft = Lead.create(UUID.randomUUID(), "Example Corp", "Technology", null, LeadDifficulty.MEDIUM);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(LeadAuthoringView.from(draft)));

        assertThat(json.path("publicDescription").asText()).isEmpty();
        assertThat(json.path("potentialValueRange").asText()).isEmpty();
        assertThat(json.path("decisionMaker").asText()).isEmpty();
        assertThat(json.path("technologyStack").asText()).isEmpty();
        assertThat(json.path("budgetSignal").asText()).isEmpty();
        assertThat(json.path("painSeverity").asText()).isEmpty();
        assertThat(json.path("signals").isArray()).isTrue();
        assertThat(json.path("signals")).isEmpty();
    }
}
