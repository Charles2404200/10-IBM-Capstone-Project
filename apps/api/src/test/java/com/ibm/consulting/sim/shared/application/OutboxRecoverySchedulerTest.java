package com.ibm.consulting.sim.shared.application;

import com.ibm.consulting.sim.shared.application.outbox.OutboxRecoveryScheduler;
import com.ibm.consulting.sim.shared.application.outbox.OutboxMetrics;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import com.ibm.consulting.sim.shared.config.KafkaProducerProperties;
import com.ibm.consulting.sim.shared.config.OutboxProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxRecoverySchedulerTest {

    @Test
    void recoversClaimsThatHaveBeenProcessingForMoreThanOneMinute() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxMetrics metrics = mock(OutboxMetrics.class);
        OutboxRecoveryScheduler scheduler = new OutboxRecoveryScheduler(
                repository,
                metrics,
                new KafkaProducerProperties(
                        Duration.ofSeconds(55), Duration.ofSeconds(30),
                        Duration.ofMillis(500), 3, 5),
                new OutboxProperties(
                        Duration.ofMillis(200), 100, Duration.ofSeconds(30),
                        Duration.ofSeconds(5), 10, "0 */10 * * * *",
                        Duration.ofDays(2), 1_000,
                        new OutboxProperties.Retry(
                                Duration.ofSeconds(1), Duration.ofMinutes(5), 2.0, 0.2))
        );
        Instant earliestExpectedCutoff = Instant.now().minusSeconds(61);

        scheduler.recoverStaleClaims();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).recoverStaleProcessing(cutoff.capture());
        verify(metrics).recordRecovered(0);
        assertFalse(cutoff.getValue().isBefore(earliestExpectedCutoff));
        assertFalse(cutoff.getValue().isAfter(Instant.now().minusSeconds(59)));
    }

}
