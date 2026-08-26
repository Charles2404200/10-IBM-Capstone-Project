package com.ibm.consulting.sim.shared.application;

import com.ibm.consulting.sim.shared.domain.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class OutboxRecoveryScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxRecoveryScheduler.class);

    private static final long STALE_CLAIM_SECONDS = 60;

    private final OutboxEventRepository repository;

    public OutboxRecoveryScheduler(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "${app.kafka.outbox.recovery-delay-ms:30000}")
    @Transactional
    public void recoverStaleClaims() {
        Instant cutoff = Instant.now().minusSeconds(STALE_CLAIM_SECONDS);
        int recoveredCount = repository.recoverStaleProcessing(cutoff);

        if (recoveredCount > 0) {
            log.warn(
                    "Recovered stale outbox claims: count={}, cutoff={}",
                    recoveredCount,
                    cutoff
            );
        }
    }
}
