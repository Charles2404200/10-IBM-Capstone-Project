package com.ibm.consulting.sim.shared.application.kafka;

import com.ibm.consulting.sim.shared.domain.kafka.KafkaInboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Bounds the durable idempotency ledger without creating large delete locks. */
@Component
public class KafkaInboxCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(KafkaInboxCleanupScheduler.class);

    private final KafkaInboxRepository repository;
    private final int retentionDays;
    private final int batchSize;

    public KafkaInboxCleanupScheduler(
            KafkaInboxRepository repository,
            @Value("${app.kafka.inbox.retention-days:14}") int retentionDays,
            @Value("${app.kafka.inbox.cleanup-batch-size:10000}") int batchSize) {
        if (retentionDays < 1 || batchSize < 1) {
            throw new IllegalArgumentException("Kafka inbox cleanup settings must be positive");
        }
        this.repository = repository;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${app.kafka.inbox.cleanup-cron:0 15 * * * *}")
    @Transactional
    public void deleteExpiredClaims() {
        Instant cutoff = Instant.now().minusSeconds(retentionDays * 86_400L);
        int deleted = repository.deleteProcessedBefore(cutoff, batchSize);
        log.debug("Completed Kafka inbox cleanup: cutoff={}, deleted={}", cutoff, deleted);
    }
}
