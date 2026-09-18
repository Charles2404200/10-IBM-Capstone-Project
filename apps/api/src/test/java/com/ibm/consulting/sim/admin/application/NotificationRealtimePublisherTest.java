package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.admin.domain.NotificationObject;
import com.ibm.consulting.sim.admin.domain.NotificationPreview;
import com.ibm.consulting.sim.admin.domain.NotificationPriority;
import com.ibm.consulting.sim.identity.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

class NotificationRealtimePublisherTest {

    @Test
    void publishesLightweightPreviewWithStableIdentityAndPriority() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        NotificationMetrics metrics = mock(NotificationMetrics.class);
        NotificationRealtimePublisher publisher = new NotificationRealtimePublisher(messaging, metrics);
        String fullMessage = "x".repeat(1_000);
        UUID eventId = UUID.randomUUID();
        NotificationObject notification = new NotificationObject(
                eventId, UUID.randomUUID(), "Course published", fullMessage,
                UserRole.LEARNER, NotificationPriority.IMPORTANT);

        publisher.publish(new NotificationPersistedEvent(notification));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/notifications/learner"),
                payload.capture());
        NotificationRealtimeSummary summary = (NotificationRealtimeSummary) payload.getValue();
        assertEquals(eventId, summary.eventId());
        assertEquals(NotificationPriority.IMPORTANT, summary.priority());
        assertEquals(NotificationPreview.MAX_CODE_POINTS,
                summary.messagePreview().codePointCount(0, summary.messagePreview().length()));
        assertNotEquals(fullMessage, summary.message());
        verify(metrics).recordWebSocketSent(NotificationPriority.IMPORTANT);
    }

    @Test
    void recordsAndContainsWebSocketFailureBecauseRestIsTheRecoveryPath() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        NotificationMetrics metrics = mock(NotificationMetrics.class);
        NotificationRealtimePublisher publisher = new NotificationRealtimePublisher(messaging, metrics);
        NotificationObject notification = new NotificationObject(
                UUID.randomUUID(), UUID.randomUUID(), "Course", "Message",
                UserRole.LEARNER, NotificationPriority.NORMAL);
        doThrow(new IllegalStateException("socket unavailable"))
                .when(messaging).convertAndSend(
                        org.mockito.ArgumentMatchers.eq("/topic/notifications/learner"),
                        org.mockito.ArgumentMatchers.any(Object.class));

        publisher.publish(new NotificationPersistedEvent(notification));

        verify(metrics).recordWebSocketFailure(NotificationPriority.NORMAL);
    }
}

