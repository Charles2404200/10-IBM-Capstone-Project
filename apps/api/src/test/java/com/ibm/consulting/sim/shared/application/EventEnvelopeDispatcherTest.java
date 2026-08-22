package com.ibm.consulting.sim.shared.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.shared.domain.EventEnvelope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventEnvelopeDispatcherTest {

    @Test
    void deserializesPayloadUsingTheRegisteredHandlerType() {
        AtomicReference<ExamplePayload> received = new AtomicReference<>();
        EventHandler<ExamplePayload> handler = new EventHandler<>() {
            @Override public String eventType() { return "example.created.v1"; }
            @Override public Class<ExamplePayload> payloadType() { return ExamplePayload.class; }
            @Override public void handle(EventEnvelope envelope, ExamplePayload payload) {
                received.set(payload);
            }
        };
        EventEnvelopeDispatcher dispatcher = new EventEnvelopeDispatcher(
                new ObjectMapper(), List.of(handler));

        dispatcher.dispatch(new EventEnvelope(
                UUID.randomUUID(),
                "example.created.v1",
                "example-1",
                1L,
                null,
                "{\"name\":\"created\"}"));

        assertEquals("created", received.get().name());
    }

    @Test
    void rejectsUnknownEventTypes() {
        EventEnvelopeDispatcher dispatcher = new EventEnvelopeDispatcher(
                new ObjectMapper(), List.of());

        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch(
                new EventEnvelope(
                        UUID.randomUUID(), "unknown.v1", null, 1L, null, "{}")));
    }

    private record ExamplePayload(String name) {
    }
}
