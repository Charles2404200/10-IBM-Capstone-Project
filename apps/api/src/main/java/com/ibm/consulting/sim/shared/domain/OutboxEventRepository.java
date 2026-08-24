package com.ibm.consulting.sim.shared.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository {

    void markPublished(OutboxEvent event);

    void markPublished(UUID eventId);

    void markPendingAgain(OutboxEvent event);

    int markProcessingIfPending(UUID eventId);

    void markPendingAgain(UUID eventId);

    void deletePublishedBefore(Instant cutoff);

    OutboxEvent createOrderedEvent(
            String eventType,
            String orderingKey,
            int sequence,
            String payload,
            String topic,
            String dest
    );

    OutboxEvent createUnOrderedEvent(
            String eventType,
            String payload,
            String topic,
            String dest
    );

    Optional<OutboxEvent> findById(UUID eventId);

    List<OutboxEvent> findDispatchableEvents(int events);
}
