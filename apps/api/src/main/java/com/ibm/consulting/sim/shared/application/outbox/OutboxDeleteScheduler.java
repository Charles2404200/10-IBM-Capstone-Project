package com.ibm.consulting.sim.shared.application.outbox;

import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import com.ibm.consulting.sim.shared.config.OutboxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;

@Component
public class OutboxDeleteScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxDeleteScheduler.class);

    private final OutboxEventRepository repository;
    private final OutboxMetrics metrics;
    private final Duration retention;
    private final int batchSize;

    public OutboxDeleteScheduler(
            OutboxEventRepository repository,
            OutboxMetrics metrics,
            OutboxProperties properties) {
        this.repository = repository;
        this.metrics = metrics;
        this.retention = properties.retention();
        this.batchSize = properties.cleanupBatchSize();
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
        Instant cutoff = Instant.now().minus(retention);

        int deleted = repository.deletePublishedBefore(cutoff, batchSize);

        // Record the affected-row count rather than one increment per scheduler
        // execution so dashboards reflect actual cleanup throughput.
        metrics.recordCleaned(deleted);

        log.debug(
                "Completed published outbox cleanup: cutoff={}, deleted={}",
                cutoff,
                deleted
        );
    }
}
