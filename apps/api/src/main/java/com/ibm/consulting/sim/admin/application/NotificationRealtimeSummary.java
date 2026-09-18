package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.admin.domain.NotificationObject;
import com.ibm.consulting.sim.admin.domain.NotificationPreview;
import com.ibm.consulting.sim.admin.domain.NotificationPriority;
import com.ibm.consulting.sim.identity.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

/** Lightweight realtime signal. REST remains the source of the complete body. */
public record NotificationRealtimeSummary(
        UUID eventId,
        String topicName,
        String messagePreview,
        String message,
        NotificationPriority priority,
        Instant createdAt,
        UserRole role) {

    public static NotificationRealtimeSummary from(NotificationObject notification) {
        String preview = NotificationPreview.from(notification.getMessage());
        return new NotificationRealtimeSummary(
                notification.getEventId(),
                notification.getTopicName(),
                preview,
                preview,
                notification.getPriority(),
                Instant.now(),
                notification.getRole());
    }
}

