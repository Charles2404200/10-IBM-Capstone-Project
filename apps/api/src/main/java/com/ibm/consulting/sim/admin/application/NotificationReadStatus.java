package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.identity.domain.UserRole;

import java.util.UUID;

/** Bounded administrator summary for the notification's current active audience. */
public record NotificationReadStatus(
        UUID eventId,
        UserRole role,
        long recipientCount,
        long readCount,
        long unreadCount) {
}
