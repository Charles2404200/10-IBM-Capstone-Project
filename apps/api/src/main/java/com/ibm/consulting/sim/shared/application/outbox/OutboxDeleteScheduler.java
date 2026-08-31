package com.ibm.consulting.sim.shared.application.outbox;

import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class OutboxDeleteScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxDeleteScheduler.class);

    private final OutboxEventRepository repository;
    private final int retentionDays;
    private final int batchSize;

    public OutboxDeleteScheduler(
            OutboxEventRepository repository,
            @Value("${app.kafka.outbox.cleanup-retention-days:2}") int retentionDays,
            @Value("${app.kafka.outbox.cleanup-batch-size:10000}") int batchSize) {
        if (retentionDays < 1 || batchSize < 1) {
            throw new IllegalArgumentException("Outbox cleanup settings must be positive");
        }
        this.repository = repository;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
    }

    /**
     * Removes successfully published outbox rows after the retention window.
     * Pending and processing events are never selected by the repository cleanup
     * query and therefore cannot be deleted by this scheduler.
     */
    @Scheduled(
            cron = "${app.kafka.outbox.cleanup-cron:0 */10 * * * *}"
    )
    @Transactional
    public void deletePublishedEvents() {
        Instant cutoff = Instant.now().minusSeconds(retentionDays * 86_400L);

        int deleted = repository.deletePublishedBefore(cutoff, batchSize);

        log.debug(
                "Completed published outbox cleanup: cutoff={}, deleted={}",
                cutoff,
                deleted
        );
    }
}
