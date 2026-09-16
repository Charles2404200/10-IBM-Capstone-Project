package com.ibm.consulting.sim.shared.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.shared.domain.outbox.EventEnvelope;
import com.ibm.consulting.sim.shared.domain.outbox.EventPriority;
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
        assertEquals(EventPriority.NORMAL, envelope.priority());
        assertEquals(occurredAt, envelope.occurredAt());
        assertEquals("{\"topicName\":\"Maintenance Notice\"}", envelope.payload());
    }

    @Test
    void legacyKafkaJsonWithoutPriorityDefaultsToNormal() throws Exception {
        String json = """
                {
                  "eventId": "3f69f319-89c4-4c45-8ae1-c5930d25af54",
                  "eventType": "LEGACY_EVENT",
                  "schemaVersion": 1,
                  "orderingMode": "UNORDERED",
                  "orderingKey": null,
                  "sequenceNumber": null,
                  "occurredAt": "2026-09-01T00:00:00Z",
                  "payload": "{}"
                }
                """;

        EventEnvelope envelope = new ObjectMapper()
                .findAndRegisterModules()
                .readValue(json, EventEnvelope.class);

        assertEquals(EventPriority.NORMAL, envelope.priority());
    }
}
