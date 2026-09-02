package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.admin.domain.NotificationPriority;

import java.time.Instant;
import java.util.UUID;

public record NotificationDetailResponse(
        UUID eventId,
        String topicName,
        String message,
        NotificationPriority priority,
        Instant createdAt,
        boolean read,
        Instant readAt) {
}

