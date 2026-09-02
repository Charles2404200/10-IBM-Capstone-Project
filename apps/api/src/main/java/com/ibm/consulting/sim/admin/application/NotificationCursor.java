package com.ibm.consulting.sim.admin.application;

import java.time.Instant;
import java.util.UUID;

public record NotificationCursor(Instant createdAt, UUID eventId) {
}

