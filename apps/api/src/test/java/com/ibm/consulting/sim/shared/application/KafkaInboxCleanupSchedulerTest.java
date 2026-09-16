package com.ibm.consulting.sim.shared.application;

import com.ibm.consulting.sim.shared.application.kafka.KafkaInboxCleanupScheduler;
import com.ibm.consulting.sim.shared.domain.kafka.KafkaInboxRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static com.ibm.consulting.sim.shared.config.TestKafkaProperties.inbox;

class KafkaInboxCleanupSchedulerTest {

    @Test
    void removesOnlyAnExpiredBoundedBatch() {
        KafkaInboxRepository repository = mock(KafkaInboxRepository.class);
        KafkaInboxCleanupScheduler scheduler = new KafkaInboxCleanupScheduler(repository, inbox());
        Instant earliestCutoff = Instant.now().minusSeconds((14 * 86_400L) + 1);

        scheduler.deleteExpiredClaims();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deleteProcessedBefore(cutoff.capture(), org.mockito.ArgumentMatchers.eq(1_000));
        assertFalse(cutoff.getValue().isBefore(earliestCutoff));
        assertFalse(cutoff.getValue().isAfter(Instant.now().minusSeconds((14 * 86_400L) - 1)));
    }
}
