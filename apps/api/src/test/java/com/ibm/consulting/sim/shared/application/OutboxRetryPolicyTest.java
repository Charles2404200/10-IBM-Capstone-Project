package com.ibm.consulting.sim.shared.application;

import com.ibm.consulting.sim.shared.application.outbox.OutboxRetryPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.ibm.consulting.sim.shared.config.TestKafkaProperties.outbox;
import static com.ibm.consulting.sim.shared.config.TestKafkaProperties.retry;

class OutboxRetryPolicyTest {

    @Test
    void growsExponentiallyAndCapsRetries() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(outbox(
                100, 10, Duration.ofDays(2), 1_000,
                retry(Duration.ofSeconds(1), Duration.ofSeconds(10), 2.0, 0.0)));
        UUID eventId = UUID.randomUUID();

        assertEquals(Duration.ofSeconds(1), policy.delayFor(eventId, 0));
        assertEquals(Duration.ofSeconds(4), policy.delayFor(eventId, 2));
        assertEquals(Duration.ofSeconds(10), policy.delayFor(eventId, 20));
    }

    @Test
    void jitterStaysWithinConfiguredBounds() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(outbox(
                100, 10, Duration.ofDays(2), 1_000,
                retry(Duration.ofSeconds(1), Duration.ofSeconds(10), 2.0, 0.2)));
        long delay = policy.delayFor(UUID.randomUUID(), 1).toMillis();

        assertTrue(delay >= 1_600 && delay <= 2_400);
    }
}
