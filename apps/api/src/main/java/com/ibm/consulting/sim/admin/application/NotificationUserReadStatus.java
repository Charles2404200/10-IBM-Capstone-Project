package com.ibm.consulting.sim.admin.application;

import java.time.Instant;
import java.util.UUID;

public record NotificationUserReadStatus(
        UUID userId,
        String displayName,
        boolean read,
        Instant readAt,
        String cursorDisplayName) {
}
