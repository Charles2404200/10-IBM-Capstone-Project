package com.ibm.consulting.sim.shared.application;

import com.ibm.consulting.sim.shared.application.outbox.OutboxRecoveryScheduler;
import com.ibm.consulting.sim.shared.application.outbox.OutboxMetrics;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

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
                55_000,
                30_000,
                5_000
        );
        Instant earliestExpectedCutoff = Instant.now().minusSeconds(61);

        scheduler.recoverStaleClaims();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).recoverStaleProcessing(cutoff.capture());
        verify(metrics).recordRecovered(0);
        assertFalse(cutoff.getValue().isBefore(earliestExpectedCutoff));
        assertFalse(cutoff.getValue().isAfter(Instant.now().minusSeconds(59)));
    }

    @Test
    void rejectsUnsafeRecoveryConfiguration() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxMetrics metrics = mock(OutboxMetrics.class);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxRecoveryScheduler(repository, metrics, 0, 30_000, 5_000)
        );
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxRecoveryScheduler(repository, metrics, 120_000, 0, 5_000)
        );
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxRecoveryScheduler(repository, metrics, 120_000, 30_000, 0)
        );
    }
}
