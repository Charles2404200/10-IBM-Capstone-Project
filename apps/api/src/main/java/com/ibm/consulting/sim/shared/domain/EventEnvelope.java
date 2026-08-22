package com.ibm.consulting.sim.shared.domain;

import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String eventType,

        // null for unordered events
        String orderingKey,

        // null for unordered events
        int sequence,

        String dest,

        String payload
) {
}
