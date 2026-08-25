package com.ibm.consulting.sim.shared.application.kafka;


import com.ibm.consulting.sim.shared.domain.EventEnvelope;
import com.ibm.consulting.sim.shared.domain.OrderingMode;

import org.springframework.kafka.core.KafkaTemplate;

import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object>
            kafkaTemplate;

    public KafkaEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<?> publish(
            String topic,
            EventEnvelope event
    ) {

        String kafkaKey =
                switch (event.orderingMode()) {

                    case ORDERED ->
                            orderedKey(event);

                    case UNORDERED ->
                            event.eventId().toString();
                };

        return kafkaTemplate.send(
                topic,
                kafkaKey,
                event
        );
    }

    private String orderedKey(
            EventEnvelope event
    ) {

        if (event.orderingKey() == null ||
                event.orderingKey().isBlank()) {

            throw new IllegalArgumentException(
                    "Ordered event requires orderingKey"
            );
        }

        return event.orderingKey();
    }
}