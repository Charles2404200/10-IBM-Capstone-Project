package com.ibm.consulting.sim.shared.application.kafka;


public record KafkaHandlerKey(
        String eventType,
        int schemaVersion
) {
}