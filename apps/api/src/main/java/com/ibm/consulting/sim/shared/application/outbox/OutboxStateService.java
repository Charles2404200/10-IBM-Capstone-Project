package com.ibm.consulting.sim.shared.application.outbox;

import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import com.ibm.consulting.sim.shared.config.OutboxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.CompletionException;
import java.util.UUID;

@Service
public class OutboxStateService {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxStateService.class);

    private final OutboxEventRepository outboxRepository;
    private final OutboxRetryPolicy retryPolicy;
    private final int maxAttempts;

    public OutboxStateService(
            OutboxEventRepository outboxRepository,
            OutboxRetryPolicy retryPolicy,
            OutboxProperties properties
    ) {
        this.outboxRepository = outboxRepository;
        this.retryPolicy = retryPolicy;
        this.maxAttempts = properties.maxAttempts();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markPublished(
            UUID eventId,
            UUID claimToken
    ) {

        int updatedRows =
                outboxRepository.markPublishedIfOwned(
                        eventId,
                        claimToken
                );

        if (updatedRows == 0) {
            log.warn(
                    "Ignoring stale publish completion: eventId={}, claimToken={}",
                    eventId,
                    claimToken
            );

            return false;
        }

        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OutboxFailureOutcome recordFailure(
            UUID eventId,
            UUID claimToken,
            int previousFailureCount,
            Throwable failure
    ) {

        if (previousFailureCount < 0) {
            throw new IllegalArgumentException("previousFailureCount must not be negative");
        }
        int currentAttempt = Math.addExact(previousFailureCount, 1);
        if (currentAttempt >= maxAttempts) {
            int updatedRows = outboxRepository.markFailedIfOwned(
                    eventId,
                    claimToken,
                    safeFailureDescription(failure)
            );
            if (updatedRows == 0) {
                log.warn(
                        "Ignoring stale terminal-failure completion: eventId={}, claimToken={}",
                        eventId,
                        claimToken
                );
                return OutboxFailureOutcome.OWNERSHIP_LOST;
            }
            return OutboxFailureOutcome.TERMINALLY_FAILED;
        }

        Instant nextAttemptAt =
                Instant.now().plus(
                        retryPolicy.delayFor(eventId, previousFailureCount)
                );

        int updatedRows =
                outboxRepository.markPendingAgainIfOwned(
                        eventId,
                        claimToken,
                        nextAttemptAt
                );

        if (updatedRows == 0) {
            log.warn(
                    "Ignoring stale retry completion: eventId={}, claimToken={}",
                    eventId,
                    claimToken
            );

            return OutboxFailureOutcome.OWNERSHIP_LOST;
        }

        return OutboxFailureOutcome.RETRY_SCHEDULED;
    }

    private String safeFailureDescription(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        String type = current == null ? "UnknownKafkaPublicationFailure" : current.getClass().getName();
        String message = current == null ? null : current.getMessage();
        if (message == null || message.isBlank()) {
            return type;
        }
        // Control characters are removed before persistence so operational tools
        // cannot be subjected to log/CSV injection by exception text.
        String boundedMessage = message.substring(0, Math.min(message.length(), 900));
        // remove the control characters and tab characters with white spaces
        String normalized = boundedMessage.replaceAll("[\\p{Cntrl}&&[^\\t]]", " ").trim();
        String description = type + ": " + normalized;
        return description.substring(0, Math.min(description.length(), 1_000));
    }
}
