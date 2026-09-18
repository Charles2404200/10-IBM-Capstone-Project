package com.ibm.consulting.sim.shared.application.outbox;

import com.ibm.consulting.sim.shared.config.OutboxProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/** Calculates capped exponential retry delays with deterministic jitter. */
@Component
public class OutboxRetryPolicy {

    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double multiplier;
    private final double jitterRatio;

    public OutboxRetryPolicy(
            OutboxProperties properties) {
        this.initialDelayMs = properties.retry().initialDelay().toMillis();
        this.maxDelayMs = properties.retry().maxDelay().toMillis();
        this.multiplier = properties.retry().multiplier();
        this.jitterRatio = properties.retry().jitterRatio();
    }

    public Duration delayFor(UUID eventId, int previousFailureCount) {
        if (previousFailureCount < 0) {
            throw new IllegalArgumentException("previousFailureCount must not be negative");
        }

        double exponential = initialDelayMs * Math.pow(multiplier, Math.min(previousFailureCount, 30));
        long nominal = Math.min(maxDelayMs, Math.round(exponential));
        long jitterWindow = Math.round(nominal * jitterRatio);
        if (jitterWindow == 0) {
            return Duration.ofMillis(nominal);
        }

        long hash = Integer.toUnsignedLong(31 * eventId.hashCode() + previousFailureCount);
        long span = (jitterWindow * 2) + 1;
        long jitter = (hash % span) - jitterWindow;
        return Duration.ofMillis(Math.max(1, Math.min(maxDelayMs, nominal + jitter)));
    }
}
