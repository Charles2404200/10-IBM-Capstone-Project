package com.ibm.consulting.sim.shared.application;

import com.ibm.consulting.sim.shared.application.outbox.OutboxRecoveryScheduler;
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
        OutboxRecoveryScheduler scheduler = new OutboxRecoveryScheduler(
                repository,
                55_000
        );
        Instant earliestExpectedCutoff = Instant.now().minusSeconds(61);

        scheduler.recoverStaleClaims();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).recoverStaleProcessing(cutoff.capture());
        assertFalse(cutoff.getValue().isBefore(earliestExpectedCutoff));
        assertFalse(cutoff.getValue().isAfter(Instant.now().minusSeconds(59)));
    }
}
