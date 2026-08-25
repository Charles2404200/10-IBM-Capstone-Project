package com.ibm.consulting.sim.shared.application;

import com.ibm.consulting.sim.shared.domain.OrderingMode;
import com.ibm.consulting.sim.shared.domain.OutboxEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutboxEventPublisherTest {

    @Test
    void orderedFactoryCreatesAPendingTransportEvent() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.ordered(
                eventId,
                "notifications",
                "NOTIFICATION_PUBLISHED",
                1,
                "learner",
                1L,
                "{\"topicName\":\"Maintenance Notice\"}");

        assertEquals(eventId, event.getId());
        assertEquals("NOTIFICATION_PUBLISHED", event.getEventType());
        assertEquals(1, event.getSchemaVersion());
        assertEquals(OrderingMode.ORDERED, event.getOrderingMode());
        assertEquals("learner", event.getOrderingKey());
        assertEquals(Long.valueOf(1L), event.getSequenceNumber());
        assertEquals("notifications", event.getTopic());
        assertEquals("{\"topicName\":\"Maintenance Notice\"}", event.getPayload());
    }
}
