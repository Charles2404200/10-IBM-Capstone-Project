package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.identity.domain.UserRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Administrator view of read state for the notification's current active audience. */
public record NotificationReadStatus(
        UUID eventId,
        UserRole role,
        int recipientCount,
        int readCount,
        int unreadCount,
        List<UserReadStatus> users) {

    public NotificationReadStatus {
        users = List.copyOf(users);
    }

    public record UserReadStatus(
            UUID userId,
            String displayName,
            boolean read,
            Instant readAt) {
    }
}
