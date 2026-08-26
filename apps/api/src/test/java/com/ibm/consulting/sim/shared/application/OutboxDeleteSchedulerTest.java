package com.ibm.consulting.sim.shared.application;

import com.ibm.consulting.sim.shared.domain.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxDeleteSchedulerTest {

    @Test
    void deletesPublishedEventsOlderThanTwoDays() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxDeleteScheduler scheduler = new OutboxDeleteScheduler(repository);
        Instant earliestExpectedCutoff = Instant.now()
                .minusSeconds((2 * 24 * 60 * 60) + 1);

        scheduler.deletePublishedEvents();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deletePublishedBefore(cutoff.capture());
        assertFalse(cutoff.getValue().isBefore(earliestExpectedCutoff));
        assertFalse(cutoff.getValue().isAfter(
                Instant.now().minusSeconds((2 * 24 * 60 * 60) - 1)
        ));
    }
}
