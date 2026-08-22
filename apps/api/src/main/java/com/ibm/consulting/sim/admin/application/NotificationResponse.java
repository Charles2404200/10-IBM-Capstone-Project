package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.admin.api.NotificationController;
import com.ibm.consulting.sim.admin.domain.UserNotification;
import com.ibm.consulting.sim.identity.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID producedByUserId,
        String message,
        UserRole role,
        Instant createdAt,
        boolean read,
        Instant readAt) {

    public static NotificationResponse from(UserNotification notification) {
        return new NotificationResponse(
                notification.eventId(),
                notification.producedByUserId(),
                notification.message(),
                notification.role(),
                notification.createdAt(),
                notification.read(),
                notification.readAt());
    }
}