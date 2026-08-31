package com.ibm.consulting.sim.shared.application;

import com.ibm.consulting.sim.shared.application.outbox.OutboxRetryPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxRetryPolicyTest {

    @Test
    void growsExponentiallyAndCapsRetries() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(1_000, 10_000, 2.0, 0.0);
        UUID eventId = UUID.randomUUID();

        assertEquals(Duration.ofSeconds(1), policy.delayFor(eventId, 0));
        assertEquals(Duration.ofSeconds(4), policy.delayFor(eventId, 2));
        assertEquals(Duration.ofSeconds(10), policy.delayFor(eventId, 20));
    }

    @Test
    void jitterStaysWithinConfiguredBounds() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(1_000, 10_000, 2.0, 0.2);
        long delay = policy.delayFor(UUID.randomUUID(), 1).toMillis();

        assertTrue(delay >= 1_600 && delay <= 2_400);
    }
}
