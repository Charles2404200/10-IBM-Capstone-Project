package com.ibm.consulting.sim.shared.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository {

    void markPublished(OutboxEvent event);

    void markPendingAgain(OutboxEvent event);

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
