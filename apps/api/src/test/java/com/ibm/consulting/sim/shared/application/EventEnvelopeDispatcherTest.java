package com.ibm.consulting.sim.shared.application;

import com.ibm.consulting.sim.shared.domain.EventEnvelope;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventEnvelopeDispatcherTest {

    @Test
    void envelopeCarriesTransportNeutralEventMetadataAndPayload() {
        UUID eventId = UUID.randomUUID();

        EventEnvelope envelope = new EventEnvelope(
                eventId,
                "NOTIFICATION_PUBLISHED",
                "learner",
                1,
                "/topic/notifications/learner",
                "{\"topicName\":\"Maintenance Notice\"}");

        assertEquals(eventId, envelope.eventId());
        assertEquals("NOTIFICATION_PUBLISHED", envelope.eventType());
        assertEquals("learner", envelope.orderingKey());
        assertEquals(1, envelope.sequence());
        assertEquals("/topic/notifications/learner", envelope.dest());
        assertEquals("{\"topicName\":\"Maintenance Notice\"}", envelope.payload());
    }
}
