package com.ibm.consulting.sim.shared.domain.kafka;

import java.time.Instant;
import java.util.UUID;

public interface KafkaInboxRepository {

    /**
     * Atomically records that a consumer group is processing an event.
     *
     * @return {@code 1} when the event was claimed, or {@code 0} when the
     *         same consumer group has already claimed that event ID
     */
    int insertIfAbsent(

            String consumerGroup,

            UUID eventId,

            String eventType,

            String topic,

            int partition,

            long offset
    );

    int deleteProcessedBefore(Instant cutoff, int limit);
}
