package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.admin.domain.NotificationPriority;

import java.time.Instant;
import java.util.UUID;

/** Immutable role-scoped content that is safe to cache independently of user read state. */
public record NotificationSharedDetail(
        UUID eventId,
        String topicName,
        String message,
        NotificationPriority priority,
        Instant createdAt) {
}

