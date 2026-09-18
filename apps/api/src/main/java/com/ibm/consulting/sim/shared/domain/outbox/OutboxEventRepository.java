package com.ibm.consulting.sim.shared.domain.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository {

    void save(OutboxEvent event);

    Optional<OutboxEvent> findById(UUID eventId);

    Optional<OutboxEvent> findByIdForUpdate(UUID eventId);

    List<OutboxEvent> findDispatchableForUpdate(int limit);

    int markPublishedIfOwned(
            UUID eventId,
            UUID claimToken
    );

    int markPendingAgainIfOwned(
            UUID eventId,
            UUID claimToken,
            Instant nextAttemptAt
    );

    int recoverStaleProcessing(
            Instant cutoff
    );

    int deletePublishedBefore(
            Instant cutoff,
            int limit
    );

    int markFailedIfOwned(
            UUID eventId,
            UUID claimToken,
            String lastError
    );
}
