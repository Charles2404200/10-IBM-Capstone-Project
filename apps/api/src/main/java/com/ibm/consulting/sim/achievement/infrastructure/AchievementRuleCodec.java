package com.ibm.consulting.sim.achievement.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.achievement.application.ConditionNode;
import org.springframework.stereotype.Component;

/** Serialises/deserialises a {@link ConditionNode} rule tree to/from the JSON string persisted on {@code Achievement.ruleJson}. */
@Component
public class AchievementRuleCodec {

    private final ObjectMapper objectMapper;

    public AchievementRuleCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(ConditionNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialise achievement rule", e);
        }
    }

    public ConditionNode decode(String json) {
        try {
            return objectMapper.readValue(json, ConditionNode.class);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse stored achievement rule", e);
        }
    }
}
