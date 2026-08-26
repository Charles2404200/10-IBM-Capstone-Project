package com.ibm.consulting.sim.shared.application.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.shared.domain.outbox.EventEnvelope;
import com.ibm.consulting.sim.shared.domain.kafka.InvalidKafkaPayloadException;
import com.ibm.consulting.sim.shared.domain.kafka.UnsupportedKafkaEventException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class KafkaEventHandlerRegistry {

    private final ObjectMapper objectMapper;

    private final Map<
            KafkaHandlerKey,
            KafkaEventHandler<?>
            > handlers;

    public KafkaEventHandlerRegistry(
            List<KafkaEventHandler<?>> handlers,
            ObjectMapper objectMapper
    ) {

        this.objectMapper = objectMapper;
        this.handlers = new HashMap<>();

        for (KafkaEventHandler<?> handler : handlers) {

            KafkaHandlerKey key =
                    new KafkaHandlerKey(
                            handler.eventType(),
                            handler.schemaVersion()
                    );

            KafkaEventHandler<?> existing =
                    this.handlers.put(key, handler);

            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate Kafka handler: " + key
                );
            }
        }
    }

    public void dispatch(
            EventEnvelope envelope,
            KafkaEventContext context
    ) {

        KafkaHandlerKey key =
                new KafkaHandlerKey(
                        envelope.eventType(),
                        envelope.schemaVersion()
                );

        KafkaEventHandler<?> handler =
                handlers.get(key);

        if (handler == null) {
            throw new UnsupportedKafkaEventException(
                    "No handler for " + key
            );
        }

        invoke(
                handler,
                envelope.payload(),
                context
        );
    }

    @SuppressWarnings("unchecked")
    private <T> void invoke(
            KafkaEventHandler<?> rawHandler,
            String json,
            KafkaEventContext context
    ) {

        KafkaEventHandler<T> handler =
                (KafkaEventHandler<T>) rawHandler;

        try {

            T payload =
                    objectMapper.readValue(
                            json,
                            handler.payloadType()
                    );

            handler.handle(
                    payload,
                    context
            );

        } catch (JsonProcessingException e) {

            throw new InvalidKafkaPayloadException(
                    "Invalid payload for eventType="
                            + handler.eventType(),
                    e
            );
        }
    }
}