package com.ibm.consulting.sim.shared.application.outbox;

import com.ibm.consulting.sim.shared.domain.outbox.EventPriority;

/** Extensible infrastructure options for an outbox enqueue operation. */
public record OutboxOptions(EventPriority priority) {

    public OutboxOptions {
        priority = priority == null ? EventPriority.NORMAL : priority;
    }

    public static OutboxOptions defaults() {
        return new OutboxOptions(EventPriority.NORMAL);
    }

    public static OutboxOptions critical() {
        return new OutboxOptions(EventPriority.CRITICAL);
    }

    static OutboxOptions normalize(OutboxOptions options) {
        return options == null ? defaults() : options;
    }
}
