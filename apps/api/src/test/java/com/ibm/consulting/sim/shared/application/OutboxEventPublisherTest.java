package com.ibm.consulting.sim.shared.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.shared.domain.OrderingMode;
import com.ibm.consulting.sim.shared.domain.OutboxEvent;
import com.ibm.consulting.sim.shared.domain.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxEventPublisherTest {

    @Test
    void serializesAnyPayloadIntoAnOrderedKafkaOutboxEvent() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxEventPublisher publisher = new OutboxEventPublisher(repository, new ObjectMapper());

        UUID eventId = publisher.publishToKafka(
                "example.created.v1",
                "aggregate-42",
                Map.of("value", 7),
                "examples");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent event = captor.getValue();
        assertEquals(eventId, event.getEventId());
        assertEquals("example.created.v1", event.getEventType());
        assertEquals("aggregate-42", event.getOrderingKey());
        assertEquals("examples", event.getTopic());
        assertEquals(OrderingMode.ORDERED, event.getOrderingMode());
        assertEquals("{\"value\":7}", event.getPayload());
        assertNotNull(event.getId());
    }
}
