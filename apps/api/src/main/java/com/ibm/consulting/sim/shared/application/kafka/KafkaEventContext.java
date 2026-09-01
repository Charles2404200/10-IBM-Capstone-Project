package com.ibm.consulting.sim.shared.application.kafka;

import com.ibm.consulting.sim.shared.domain.outbox.EventPriority;
import com.ibm.consulting.sim.shared.domain.outbox.OrderingMode;

import java.time.Instant;
import java.util.UUID;

public record KafkaEventContext(

        UUID eventId,

        String topic,

        int partition,

        long offset,

        String kafkaKey,

        String eventType,

        int schemaVersion,

        OrderingMode orderingMode,

        String orderingKey,

        Long sequenceNumber,

        EventPriority priority,

        Instant occurredAt
) {
}
