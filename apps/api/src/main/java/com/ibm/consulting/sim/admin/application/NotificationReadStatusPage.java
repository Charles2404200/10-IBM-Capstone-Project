package com.ibm.consulting.sim.admin.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotificationReadStatusPage(
        List<UserReadStatus> items,
        String nextCursor,
        boolean hasMore) {

    public NotificationReadStatusPage {
        items = List.copyOf(items);
    }

    public record UserReadStatus(
            UUID userId,
            String displayName,
            boolean read,
            Instant readAt) {
    }
}
