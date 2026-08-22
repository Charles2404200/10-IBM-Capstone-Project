package com.ibm.consulting.sim.admin.domain;

import com.ibm.consulting.sim.identity.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

/** A notification enriched with the requesting user's read state. */
public record UserNotification(
        UUID eventId,
        UUID producedByUserId,
        String message,
        UserRole role,
        Instant createdAt,
        Instant readAt) {

    public boolean read() {
        return readAt != null;
    }
}
