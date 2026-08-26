package com.ibm.consulting.sim.admin.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.admin.infrastructure.NotificationKafkaProperties;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.shared.config.KafkaConsumerProperties;
import com.ibm.consulting.sim.shared.config.KafkaTopicProperties;
import com.ibm.consulting.sim.shared.domain.EventSequenceRepository;
import com.ibm.consulting.sim.shared.domain.OutboxEvent;
import com.ibm.consulting.sim.shared.domain.OutboxEventRepository;
import com.ibm.consulting.sim.shared.domain.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminNotificationServiceTest {

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
                    properties,
                    sequenceRepository,
                    outboxRepository,
                    new ObjectMapper()
            );

    @Test
    void queuesOneOrderedOutboxEventPerDistinctRole() {
        when(sequenceRepository.next("notifications:LEARNER"))
                .thenReturn(7L);
        NotificationPublishResult result = service.notifyRoles(
                UUID.randomUUID(),
                "Maintenance",
                "Maintenance begins shortly.",
                List.of(UserRole.LEARNER, UserRole.LEARNER)
        );

        assertEquals(1, result.publishedCount());
        assertEquals(List.of(UserRole.LEARNER), result.roles());

        ArgumentCaptor<OutboxEvent> outboxCaptor =
                ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());

        OutboxEvent outbox = outboxCaptor.getValue();
        assertEquals("notifications", outbox.getTopic());
        assertEquals("NOTIFICATION_PUBLISHED", outbox.getEventType());
        assertEquals(1, outbox.getSchemaVersion());
        assertEquals("notifications:LEARNER", outbox.getOrderingKey());
        assertEquals(Long.valueOf(7L), outbox.getSequenceNumber());
        assertTrue(outbox.getPayload().contains("Maintenance"));
        assertEquals(OutboxStatus.PENDING, outbox.getStatus());
    }
}
