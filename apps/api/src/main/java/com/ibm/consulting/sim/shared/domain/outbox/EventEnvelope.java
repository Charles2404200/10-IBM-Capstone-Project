package com.ibm.consulting.sim.shared.domain.outbox;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(

        UUID eventId,

        String eventType,

        int schemaVersion,

        OrderingMode orderingMode,

        String orderingKey,

        Long sequenceNumber,

        EventPriority priority,

        Instant occurredAt,

        String payload
) {

    /** Source-compatible constructor for callers created before priority support. */
    public EventEnvelope(
            UUID eventId,
            String eventType,
            int schemaVersion,
            OrderingMode orderingMode,
            String orderingKey,
            Long sequenceNumber,
            Instant occurredAt,
            String payload
    ) {
        this(
                eventId,
                eventType,
                schemaVersion,
                orderingMode,
                orderingKey,
                sequenceNumber,
                EventPriority.NORMAL,
                occurredAt,
                payload
        );
    }

    public EventEnvelope {

        if (eventId == null) {
            throw new IllegalArgumentException(
                    "eventId is required"
            );
        }

        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException(
                    "eventType is required"
            );
        }

        if (schemaVersion <= 0) {
            throw new IllegalArgumentException(
                    "schemaVersion must be positive"
            );
        }

        if (orderingMode == null) {
            throw new IllegalArgumentException(
                    "orderingMode is required"
            );
        }

        // Kafka records produced before priority support omit this field.
        // Treat them exactly like legacy outbox rows: NORMAL.
        priority = priority == null ? EventPriority.NORMAL : priority;

        if (orderingMode == OrderingMode.ORDERED) {

            if (orderingKey == null ||
                    orderingKey.isBlank()) {

                throw new IllegalArgumentException(
                        "Ordered event requires orderingKey"
                );
            }

            if (sequenceNumber == null ||
                    sequenceNumber <= 0) {

                throw new IllegalArgumentException(
                        "Ordered event requires sequenceNumber"
                );
            }
        }
    }
}
