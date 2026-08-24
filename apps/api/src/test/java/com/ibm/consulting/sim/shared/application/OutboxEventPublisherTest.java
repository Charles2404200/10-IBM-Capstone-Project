package com.ibm.consulting.sim.shared.application;

import com.ibm.consulting.sim.shared.domain.OrderingMode;
import com.ibm.consulting.sim.shared.domain.OutboxEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OutboxEventPublisherTest {

    @Test
    void orderedFactoryCreatesAPendingTransportEvent() {
        OutboxEvent event = OutboxEvent.ordered(
                "NOTIFICATION_PUBLISHED",
                "learner",
                1,
                "{\"topicName\":\"Maintenance Notice\"}",
                "notifications",
                "/topic/notifications/learner");

        assertNotNull(event.getId());
        assertEquals("NOTIFICATION_PUBLISHED", event.getEventType());
        assertEquals(OrderingMode.ORDERED, event.getOrderingMode());
        assertEquals("learner", event.getOrderingKey());
        assertEquals(1, event.getSequenceNumber());
        assertEquals("notifications", event.getTopic());
        assertEquals("/topic/notifications/learner", event.getDest());
        assertEquals("{\"topicName\":\"Maintenance Notice\"}", event.getPayload());
    }
}
