package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.identity.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID producedByUserId,
        String topicName,
        String message,
        UserRole role,
        Instant createdAt,
        boolean read,
        Instant readAt) {
}
