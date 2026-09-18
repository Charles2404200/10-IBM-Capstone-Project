package com.ibm.consulting.sim.shared.domain.outbox;

/**
 * Infrastructure scheduling priority for eligible outbox events.
 *
 * <p>Weights are persisted instead of enum ordinals so adding enum constants
 * cannot silently reinterpret existing rows.</p>
 */
public enum EventPriority {
    LOW((short) 100),
    NORMAL((short) 200),
    HIGH((short) 300),
    CRITICAL((short) 400);

    private final short weight;

    EventPriority(short weight) {
        this.weight = weight;
    }

    public short weight() {
        return weight;
    }

    public static EventPriority fromWeight(short weight) {
        for (EventPriority priority : values()) {
            if (priority.weight == weight) {
                return priority;
            }
        }
        throw new IllegalArgumentException("Unknown event priority weight: " + weight);
    }
}
