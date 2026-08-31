package com.ibm.consulting.sim.shared.email.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Isolates non-critical email-provider latency from user-facing mutations.
 *
 * <p>Delivery starts only once the source transaction has committed and uses a
 * bounded executor. A small bounded retry handles transient SMTP failures while
 * keeping registration and credential recovery APIs responsive.</p>
 */
@Component
public class AsyncTransactionalEmailDispatcher {
    private static final Logger log = LoggerFactory.getLogger(AsyncTransactionalEmailDispatcher.class);

    private final EmailDeliveryGateway emailDeliveryGateway;
    private final int maxAttempts;
    private final long initialBackoffMs;

    public AsyncTransactionalEmailDispatcher(
            EmailDeliveryGateway emailDeliveryGateway,
            @Value("${app.email.delivery.max-attempts:3}") int maxAttempts,
            @Value("${app.email.delivery.initial-backoff-ms:250}") long initialBackoffMs) {
        this.emailDeliveryGateway = emailDeliveryGateway;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoffMs = Math.max(0, initialBackoffMs);
    }

    @Async("transactionalEmailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(TransactionalEmailRequestedEvent event) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                emailDeliveryGateway.send(event.email());
                log.info("Transactional email delivered: recipient={}", mask(event.email().recipient()));
                return;
            } catch (RuntimeException deliveryFailure) {
                lastFailure = deliveryFailure;
                log.warn("Transactional email delivery attempt {}/{} failed for recipient={}",
                        attempt, maxAttempts, mask(event.email().recipient()));
                waitBeforeRetry(attempt);
            }
        }
        log.error("Transactional email delivery exhausted retries for recipient={}",
                mask(event.email().recipient()), lastFailure);
    }

    private void waitBeforeRetry(int completedAttempt) {
        if (completedAttempt >= maxAttempts || initialBackoffMs == 0) return;
        try {
            Thread.sleep(initialBackoffMs * completedAttempt);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
