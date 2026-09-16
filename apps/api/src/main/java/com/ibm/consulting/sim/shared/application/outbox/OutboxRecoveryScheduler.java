package com.ibm.consulting.sim.shared.application.outbox;

import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import com.ibm.consulting.sim.shared.config.KafkaProducerProperties;
import com.ibm.consulting.sim.shared.config.OutboxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
public class OutboxRecoveryScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxRecoveryScheduler.class);

    private final OutboxEventRepository repository;
    private final OutboxMetrics metrics;

    // Recovery must wait longer than Kafka is legally allowed to keep a send in
    // flight. Recovering earlier could let two workers publish the same event
    // concurrently while the original producer request is still unresolved.
    private final Duration staleThreshold;

    public OutboxRecoveryScheduler(
            OutboxEventRepository repository,
            OutboxMetrics metrics,
            KafkaProducerProperties producerProperties,
            OutboxProperties outboxProperties
    ) {
        this.repository = repository;
        this.metrics = metrics;
        // The scheduled polling delay controls how often recovery runs; it is
        // validated above but intentionally does not shorten the lease itself.
        this.staleThreshold = producerProperties.deliveryTimeout()
                .plus(outboxProperties.recoverySafetyMargin());
    }

    @Scheduled(fixedDelayString = "#{@outboxScheduleIntervals.recoveryDelayMillis()}")
    @Transactional
    public void recoverStaleClaims() {

        Instant cutoff = Instant.now().minus(staleThreshold);

        int recoveredCount =
                repository.recoverStaleProcessing(cutoff);

        metrics.recordRecovered(recoveredCount);

        if (recoveredCount > 0) {
            log.warn(
                    "Recovered stale outbox claims: count={}, cutoff={}",
                    recoveredCount,
                    cutoff
            );
        }
    }
}
