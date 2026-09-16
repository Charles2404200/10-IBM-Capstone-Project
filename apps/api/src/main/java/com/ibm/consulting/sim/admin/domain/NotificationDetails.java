package com.ibm.consulting.sim.admin.domain;

import com.ibm.consulting.sim.identity.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

/** Stored notification data independent of any particular recipient. */
public record NotificationDetails(
        UUID eventId,
        UUID producedByUserId,
        String message,
        UserRole role,
        Instant createdAt) {
}
