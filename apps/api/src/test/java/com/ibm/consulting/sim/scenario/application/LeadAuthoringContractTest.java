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
    void incompleteDraftLeadSerializesNullableFieldsAndAnEmptySignalsArray() throws Exception {
        Lead draft = Lead.create(UUID.randomUUID(), "Example Corp", "Technology", null, LeadDifficulty.MEDIUM);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(LeadAuthoringView.from(draft)));

        assertThat(json.path("publicDescription").isNull()).isTrue();
        assertThat(json.path("potentialValueRange").isNull()).isTrue();
        assertThat(json.path("decisionMaker").isNull()).isTrue();
        assertThat(json.path("technologyStack").isNull()).isTrue();
        assertThat(json.path("budgetSignal").isNull()).isTrue();
        assertThat(json.path("painSeverity").isNull()).isTrue();
        assertThat(json.path("signals").isArray()).isTrue();
        assertThat(json.path("signals")).isEmpty();
    }
}
