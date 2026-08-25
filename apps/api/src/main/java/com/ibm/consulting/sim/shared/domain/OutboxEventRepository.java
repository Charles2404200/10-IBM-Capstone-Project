package com.ibm.consulting.sim.shared.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository {

    void save(OutboxEvent event);

    Optional<OutboxEvent> findById(UUID eventId);

    Optional<OutboxEvent> findByIdForUpdate(UUID eventId);

    List<OutboxEvent> findDispatchableForUpdate(int limit);

    void deletePublishedBefore(Instant cutoff);
}
