package com.ibm.consulting.sim.admin.domain;

import com.ibm.consulting.sim.shared.domain.outbox.EventPriority;

/** User-facing importance for administrator-authored notifications. */
public enum NotificationPriority {
    NORMAL,
    IMPORTANT,
    CRITICAL;

    public EventPriority toEventPriority() {
        return switch (this) {
            case NORMAL -> EventPriority.NORMAL;
            case IMPORTANT -> EventPriority.HIGH;
            case CRITICAL -> EventPriority.CRITICAL;
        };
    }

    public static NotificationPriority normalize(NotificationPriority priority) {
        return priority == null ? NORMAL : priority;
    }
}
