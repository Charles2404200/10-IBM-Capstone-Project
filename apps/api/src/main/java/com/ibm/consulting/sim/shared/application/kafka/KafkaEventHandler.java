package com.ibm.consulting.sim.shared.application.kafka;

public interface KafkaEventHandler<T> {

    String eventType();

    int schemaVersion();

    Class<T> payloadType();

    void handle(
            T payload,
            KafkaEventContext context
    );
}