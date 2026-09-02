package com.ibm.consulting.sim.shared.application;

import com.ibm.consulting.sim.shared.application.outbox.OutboxDeleteScheduler;
import com.ibm.consulting.sim.shared.application.outbox.OutboxMetrics;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxDeleteSchedulerTest {

    @Test
    void deletesPublishedEventsOlderThanTwoDays() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxMetrics metrics = mock(OutboxMetrics.class);
        OutboxDeleteScheduler scheduler = new OutboxDeleteScheduler(repository, metrics, 2, 500);
        Instant earliestExpectedCutoff = Instant.now()
                .minusSeconds((2 * 24 * 60 * 60) + 1);

        scheduler.deletePublishedEvents();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deletePublishedBefore(cutoff.capture(), org.mockito.ArgumentMatchers.eq(500));
        verify(metrics).recordCleaned(0);
        assertFalse(cutoff.getValue().isBefore(earliestExpectedCutoff));
        assertFalse(cutoff.getValue().isAfter(
                Instant.now().minusSeconds((2 * 24 * 60 * 60) - 1)
        ));
    }

    @Test
    void rejectsNonPositiveRetentionAndBatchSize() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxMetrics metrics = mock(OutboxMetrics.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxDeleteScheduler(repository, metrics, 0, 500)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxDeleteScheduler(repository, metrics, 2, 0)
        );
    }
}
