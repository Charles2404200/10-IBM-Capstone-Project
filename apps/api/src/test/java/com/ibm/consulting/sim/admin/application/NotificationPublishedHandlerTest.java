package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.admin.domain.NotificationEvent;
import com.ibm.consulting.sim.admin.domain.NotificationObject;
import com.ibm.consulting.sim.admin.domain.NotificationPriority;
import com.ibm.consulting.sim.admin.domain.NotificationRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.shared.application.kafka.KafkaEventContext;
import com.ibm.consulting.sim.shared.domain.kafka.InvalidKafkaEventException;
import com.ibm.consulting.sim.shared.domain.outbox.EventPriority;
import com.ibm.consulting.sim.shared.domain.outbox.OrderingMode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class NotificationPublishedHandlerTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final NotificationPublishedHandler handler =
            new NotificationPublishedHandler(repository, events);

    @Test
    void persistsAndPublishesCriticalNotificationPriority() {
        UUID eventId = UUID.randomUUID();
        NotificationObject payload = new NotificationObject(
                eventId, UUID.randomUUID(), "Security", "Reset now",
                UserRole.LEARNER, NotificationPriority.CRITICAL);

        handler.handle(payload, context(eventId, EventPriority.CRITICAL));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(repository).save(captor.capture());
        assertEquals(NotificationPriority.CRITICAL, captor.getValue().getPriority());
        verify(events).publishEvent(new NotificationPersistedEvent(payload));
    }

    @Test
    void persistsImportantNotificationWhenEnvelopeUsesHighInfrastructurePriority() {
        UUID eventId = UUID.randomUUID();
        NotificationObject notification = new NotificationObject(
                eventId, UUID.randomUUID(), "New course published", "A new course is available.",
                UserRole.LEARNER, NotificationPriority.IMPORTANT);

        handler.handle(notification, context(eventId, EventPriority.HIGH));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(repository).save(captor.capture());
        assertEquals(NotificationPriority.IMPORTANT, captor.getValue().getPriority());
    }

    @Test
    void rejectsEnvelopeAndPayloadPriorityMismatch() {
        UUID eventId = UUID.randomUUID();
        NotificationObject payload = new NotificationObject(
                eventId, UUID.randomUUID(), "Security", "Reset now",
                UserRole.LEARNER, NotificationPriority.CRITICAL);

        assertThrows(
                InvalidKafkaEventException.class,
                () -> handler.handle(payload, context(eventId, EventPriority.NORMAL))
        );

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private KafkaEventContext context(UUID eventId, EventPriority priority) {
        return new KafkaEventContext(
                eventId,
                "notifications",
                0,
                10L,
                "notifications:LEARNER",
                NotificationPublishedHandler.EVENT_TYPE,
                1,
                OrderingMode.ORDERED,
                "notifications:LEARNER",
                1L,
                priority,
                Instant.now()
        );
    }
}
