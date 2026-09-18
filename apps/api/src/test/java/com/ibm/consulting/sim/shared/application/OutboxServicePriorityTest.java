package com.ibm.consulting.sim.shared.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.shared.application.outbox.OutboxOptions;
import com.ibm.consulting.sim.shared.application.outbox.OutboxService;
import com.ibm.consulting.sim.shared.domain.outbox.EventPriority;
import com.ibm.consulting.sim.shared.domain.outbox.EventSequenceRepository;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEvent;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxServicePriorityTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final EventSequenceRepository sequences = mock(EventSequenceRepository.class);
    private final OutboxService service = new OutboxService(repository, sequences, new ObjectMapper());

    @Test
    void legacyOrderedEnqueueDefaultsToNormal() {
        when(sequences.next("meeting:1")).thenReturn(1L);

        service.enqueueOrdered("events", "READY", 1, "meeting:1", new Payload("ready"));

        assertEquals(EventPriority.NORMAL, savedEvent().getEventPriority());
        assertEquals((short) 200, savedEvent().getPriorityWeight());
    }

    @Test
    void explicitOptionsPersistCriticalPriority() {
        when(sequences.next("meeting:2")).thenReturn(4L);

        service.enqueueOrdered(
                "events", "SAFETY_INTERVENTION", 1, "meeting:2",
                new Payload("stop"), OutboxOptions.critical());

        assertEquals(EventPriority.CRITICAL, savedEvent().getEventPriority());
        assertEquals((short) 400, savedEvent().getPriorityWeight());
    }

    @Test
    void nullOptionsAreNormalizedAtTheServiceBoundary() {
        service.enqueueUnordered("events", "LEGACY", 1, new Payload("legacy"), null);

        assertEquals(EventPriority.NORMAL, savedEvent().getEventPriority());
    }

    private OutboxEvent savedEvent() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private record Payload(String value) {}
}
