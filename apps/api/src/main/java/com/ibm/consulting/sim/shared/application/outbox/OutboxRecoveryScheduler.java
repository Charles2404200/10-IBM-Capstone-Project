package com.ibm.consulting.sim.shared.application.outbox;

import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
public class OutboxRecoveryScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxRecoveryScheduler.class);

    private static final Duration RECOVERY_SAFETY_MARGIN =
            Duration.ofSeconds(5);

    @Value("${app.kafka.producer.delivery-timeout-ms}")
    private long deliveryTimeoutMs;

    private final OutboxEventRepository repository;

    public OutboxRecoveryScheduler(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "${app.kafka.outbox.recovery-delay-ms:30000}")
    @Transactional
    public void recoverStaleClaims() {

        Duration staleThreshold =
                Duration.ofMillis(deliveryTimeoutMs)
                        .plus(RECOVERY_SAFETY_MARGIN);

        Instant cutoff = Instant.now().minus(staleThreshold);

        int recoveredCount =
                repository.recoverStaleProcessing(cutoff);

        if (recoveredCount > 0) {
            log.warn(
                    "Recovered stale outbox claims: count={}, cutoff={}",
                    recoveredCount,
                    cutoff
            );
        }
    }
}