package com.ibm.consulting.sim.shared.application;

import com.ibm.consulting.sim.shared.domain.outbox.EventEnvelope;
import com.ibm.consulting.sim.shared.domain.outbox.OrderingMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventEnvelopeDispatcherTest {

    @Test
    void envelopeCarriesTransportNeutralEventMetadataAndPayload() {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        EventEnvelope envelope = new EventEnvelope(
                eventId,
                "NOTIFICATION_PUBLISHED",
                1,
                OrderingMode.ORDERED,
                "learner",
                1L,
                occurredAt,
                "{\"topicName\":\"Maintenance Notice\"}");

        assertEquals(eventId, envelope.eventId());
        assertEquals("NOTIFICATION_PUBLISHED", envelope.eventType());
        assertEquals(1, envelope.schemaVersion());
        assertEquals(OrderingMode.ORDERED, envelope.orderingMode());
        assertEquals("learner", envelope.orderingKey());
        assertEquals(Long.valueOf(1L), envelope.sequenceNumber());
        assertEquals(occurredAt, envelope.occurredAt());
        assertEquals("{\"topicName\":\"Maintenance Notice\"}", envelope.payload());
    }
}
