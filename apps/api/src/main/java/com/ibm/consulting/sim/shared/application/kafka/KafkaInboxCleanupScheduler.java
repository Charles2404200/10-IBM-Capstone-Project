package com.ibm.consulting.sim.shared.application.kafka;

import com.ibm.consulting.sim.shared.domain.kafka.KafkaInboxRepository;
import com.ibm.consulting.sim.shared.config.KafkaInboxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;

/** Bounds the durable idempotency ledger without creating large delete locks. */
@Component
public class KafkaInboxCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(KafkaInboxCleanupScheduler.class);

    private final KafkaInboxRepository repository;
    private final Duration retention;
    private final int batchSize;

    public KafkaInboxCleanupScheduler(
            KafkaInboxRepository repository,
            KafkaInboxProperties properties) {
        this.repository = repository;
        this.retention = properties.retention();
        this.batchSize = properties.cleanupBatchSize();
    }

    @Scheduled(cron = "${app.kafka.inbox.cleanup-cron:0 15 * * * *}")
    @Transactional
    public void deleteExpiredClaims() {
        Instant cutoff = Instant.now().minus(retention);
        int deleted = repository.deleteProcessedBefore(cutoff, batchSize);
        log.debug("Completed Kafka inbox cleanup: cutoff={}, deleted={}", cutoff, deleted);
    }
}
