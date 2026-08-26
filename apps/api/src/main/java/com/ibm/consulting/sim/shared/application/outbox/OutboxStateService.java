package com.ibm.consulting.sim.shared.application.outbox;

import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxStateService {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxStateService.class);

    private static final Duration RETRY_DELAY =
            Duration.ofSeconds(1);

    private final OutboxEventRepository outboxRepository;

    public OutboxStateService(
            OutboxEventRepository outboxRepository
    ) {
        this.outboxRepository = outboxRepository;
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
            UUID claimToken
    ) {

        Instant nextAttemptAt =
                Instant.now().plus(RETRY_DELAY);

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