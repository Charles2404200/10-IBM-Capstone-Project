package com.ibm.consulting.sim.shared.application.outbox;

import org.springframework.beans.factory.annotation.Value;
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
            @Value("${app.kafka.outbox.retry.initial-delay-ms:1000}") long initialDelayMs,
            @Value("${app.kafka.outbox.retry.max-delay-ms:300000}") long maxDelayMs,
            @Value("${app.kafka.outbox.retry.multiplier:2.0}") double multiplier,
            @Value("${app.kafka.outbox.retry.jitter-ratio:0.2}") double jitterRatio) {
        if (initialDelayMs <= 0 || maxDelayMs < initialDelayMs) {
            throw new IllegalArgumentException("Invalid outbox retry delay range");
        }
        if (multiplier < 1.0 || jitterRatio < 0.0 || jitterRatio > 1.0) {
            throw new IllegalArgumentException("Invalid outbox retry multiplier or jitter ratio");
        }
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.multiplier = multiplier;
        this.jitterRatio = jitterRatio;
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
