package com.ibm.consulting.sim.admin.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.admin.infrastructure.NotificationKafkaProperties;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.shared.application.kafka.KafkaEventPublisher;
import com.ibm.consulting.sim.shared.config.KafkaConsumerProperties;
import com.ibm.consulting.sim.shared.config.KafkaTopicProperties;
import com.ibm.consulting.sim.shared.domain.EventEnvelope;
import com.ibm.consulting.sim.shared.domain.EventSequenceRepository;
import com.ibm.consulting.sim.shared.domain.OrderingMode;
import com.ibm.consulting.sim.shared.domain.OutboxEvent;
import com.ibm.consulting.sim.shared.domain.OutboxEventRepository;
import com.ibm.consulting.sim.shared.domain.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminNotificationServiceTest {

    private final KafkaEventPublisher kafkaPublisher =
            mock(KafkaEventPublisher.class);
    private final EventSequenceRepository sequenceRepository =
            mock(EventSequenceRepository.class);
    private final OutboxEventRepository outboxRepository =
            mock(OutboxEventRepository.class);
    private final NotificationKafkaProperties properties =
            new NotificationKafkaProperties(
                    new KafkaTopicProperties("notifications", 1, 1),
                    new KafkaConsumerProperties("notification-consumer", 1)
            );
    private final AdminNotificationService service =
            new AdminNotificationService(
                    kafkaPublisher,
                    properties,
                    sequenceRepository,
                    outboxRepository,
                    new ObjectMapper()
            );

    @Test
    void publishesDirectlyThroughSharedKafkaPublisher() {
        when(sequenceRepository.next("notifications:LEARNER"))
                .thenReturn(7L);
        doReturn(CompletableFuture.completedFuture(null))
                .when(kafkaPublisher)
                .publish(eq("notifications"), any(EventEnvelope.class));

        NotificationPublishResult result = service.notifyRoles(
                UUID.randomUUID(),
                "Maintenance",
                "Maintenance begins shortly.",
                List.of(UserRole.LEARNER, UserRole.LEARNER)
        ).join();

        assertEquals(1, result.publishedCount());
        assertEquals(List.of(UserRole.LEARNER), result.roles());

        ArgumentCaptor<EventEnvelope> envelopeCaptor =
                ArgumentCaptor.forClass(EventEnvelope.class);
        verify(kafkaPublisher).publish(
                eq("notifications"),
                envelopeCaptor.capture()
        );

        EventEnvelope envelope = envelopeCaptor.getValue();
        assertEquals("NOTIFICATION_PUBLISHED", envelope.eventType());
        assertEquals(1, envelope.schemaVersion());
        assertEquals(OrderingMode.ORDERED, envelope.orderingMode());
        assertEquals("notifications:LEARNER", envelope.orderingKey());
        assertEquals(Long.valueOf(7L), envelope.sequenceNumber());
        assertTrue(envelope.payload().contains("Maintenance"));
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void savesSameOrderedEventToOutboxWhenDirectPublicationFails() {
        when(sequenceRepository.next("notifications:REVIEWER"))
                .thenReturn(11L);
        doReturn(CompletableFuture.failedFuture(
                new IllegalStateException("Kafka unavailable")
        )).when(kafkaPublisher).publish(
                eq("notifications"),
                any(EventEnvelope.class)
        );

        NotificationPublishResult result = service.notifyRoles(
                UUID.randomUUID(),
                "Review required",
                "A scenario is ready for review.",
                List.of(UserRole.REVIEWER)
        ).join();

        assertEquals(1, result.publishedCount());

        ArgumentCaptor<EventEnvelope> envelopeCaptor =
                ArgumentCaptor.forClass(EventEnvelope.class);
        verify(kafkaPublisher).publish(
                eq("notifications"),
                envelopeCaptor.capture()
        );

        ArgumentCaptor<OutboxEvent> outboxCaptor =
                ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());

        EventEnvelope envelope = envelopeCaptor.getValue();
        OutboxEvent outbox = outboxCaptor.getValue();
        assertEquals(envelope.eventId(), outbox.getId());
        assertEquals(envelope.eventType(), outbox.getEventType());
        assertEquals(envelope.schemaVersion(), outbox.getSchemaVersion());
        assertEquals(envelope.orderingKey(), outbox.getOrderingKey());
        assertEquals(envelope.sequenceNumber(), outbox.getSequenceNumber());
        assertEquals(envelope.payload(), outbox.getPayload());
        assertEquals(OutboxStatus.PENDING, outbox.getStatus());
    }
}
