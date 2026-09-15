package com.ibm.consulting.sim.scenario.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.scenario.domain.InvalidScenarioLifecycleDefinitionException;
import com.ibm.consulting.sim.scenario.domain.LifecycleConditionNode;
import com.ibm.consulting.sim.scenario.domain.LifecycleConditionType;
import com.ibm.consulting.sim.scenario.domain.ScenarioLifecycleDefinition;
import com.ibm.consulting.sim.scenario.domain.ScenarioObjectiveDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LifecycleDefinitionCodecTest {

    private final LifecycleDefinitionCodec codec = new LifecycleDefinitionCodec(new ObjectMapper());

    @Test
    void roundTripsAValidatedDefinition() {
        ScenarioLifecycleDefinition defaults = ScenarioLifecycleDefinition.defaults();
        ScenarioLifecycleDefinition definition = new ScenarioLifecycleDefinition(defaults.schemaVersion(),
                defaults.stages(), List.of(new ScenarioObjectiveDefinition("MAIN_OBJECTIVE", null, "COMPLETED",
                        "Win client approval", "Complete the consulting engagement.", true, 0,
                        LifecycleConditionNode.leaf(LifecycleConditionType.CURRENT_STAGE_COMPLETED))));

        assertThat(codec.decode(codec.encode(definition))).isEqualTo(definition);
    }

    @Test
    void nullStoredJsonUsesDefaultsButBlankDoesNot() {
        assertThat(codec.decode(null)).isEqualTo(ScenarioLifecycleDefinition.defaults());
        assertThat(codec.decode(null, "Legacy objective").objectives()).singleElement()
                .extracting(ScenarioObjectiveDefinition::title).isEqualTo("Legacy objective");
        assertThatThrownBy(() -> codec.decode("  \t "))
                .isInstanceOf(InvalidScenarioLifecycleDefinitionException.class);
    }

    @Test
    void rejectsMalformedUnknownAndInvalidSchemaJson() {
        assertThatThrownBy(() -> codec.decode("{not-json"))
                .isInstanceOf(InvalidScenarioLifecycleDefinitionException.class)
                .hasMessageContaining("valid JSON");
        assertThatThrownBy(() -> codec.decode("{\"schemaVersion\":1,\"stages\":[],\"objectives\":[],\"executable\":\"evil\"}"))
                .isInstanceOf(InvalidScenarioLifecycleDefinitionException.class);
        assertThatThrownBy(() -> codec.decode("{\"schemaVersion\":99,\"stages\":[],\"objectives\":[]}"))
                .isInstanceOf(InvalidScenarioLifecycleDefinitionException.class)
                .hasMessageContaining("schema version");
        assertThatThrownBy(() -> codec.decode("{\"schemaVersion\":1,\"stages\":[],\"objectives\":[{"
                + "\"key\":\"X\",\"stageKey\":\"LEAD\",\"title\":\"X\",\"required\":true,"
                + "\"displayOrder\":0,\"completionCondition\":{\"kind\":\"LEAF\","
                + "\"conditionType\":\"RUN_JAVASCRIPT\"}}]}"))
                .isInstanceOf(InvalidScenarioLifecycleDefinitionException.class);
    }

    @Test
    void rejectsOversizedJsonAndInvalidDefinitionOnEncode() {
        assertThatThrownBy(() -> codec.decode(" ".repeat(LifecycleDefinitionCodec.MAX_JSON_LENGTH + 1)))
                .isInstanceOf(InvalidScenarioLifecycleDefinitionException.class)
                .hasMessageContaining("too large");
        assertThatThrownBy(() -> codec.encode(new ScenarioLifecycleDefinition(2, List.of(), List.of())))
                .isInstanceOf(InvalidScenarioLifecycleDefinitionException.class);
        assertThatThrownBy(() -> codec.encode(null))
                .isInstanceOf(InvalidScenarioLifecycleDefinitionException.class);
    }
}
