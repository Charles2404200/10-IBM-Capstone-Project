package com.ibm.consulting.sim.shared.application.outbox;

import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxStateService {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxStateService.class);

    private final OutboxEventRepository outboxRepository;
    private final OutboxRetryPolicy retryPolicy;

    public OutboxStateService(
            OutboxEventRepository outboxRepository,
            OutboxRetryPolicy retryPolicy
    ) {
        this.outboxRepository = outboxRepository;
        this.retryPolicy = retryPolicy;
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
    public boolean markPendingAgain(
            UUID eventId,
            UUID claimToken,
            int previousFailureCount
    ) {

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

            return false;
        }

        return true;
    }
}
