package com.ibm.consulting.sim.admin.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProjectedNotification(
        Map<String, Object> fields,
        Instant cursorCreatedAt,
        UUID cursorEventId) {
}

