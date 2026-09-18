package com.ibm.consulting.sim.shared.config;

import org.springframework.stereotype.Component;

/**
 * Exposes validated outbox durations in the numeric form required by
 * {@code @Scheduled} in the Spring Framework version used by this service.
 *
 * <p>Keeping this conversion in one bean lets configuration continue using
 * readable {@link java.time.Duration} values such as {@code 200ms} while the
 * scheduler receives an unambiguous millisecond value.</p>
 */
@Component("outboxScheduleIntervals")
public final class OutboxScheduleIntervals {

    private final OutboxProperties properties;

    public OutboxScheduleIntervals(OutboxProperties properties) {
        this.properties = properties;
    }

    public String pollDelayMillis() {
        return Long.toString(properties.pollDelay().toMillis());
    }

    public String recoveryDelayMillis() {
        return Long.toString(properties.recoveryDelay().toMillis());
    }
}
