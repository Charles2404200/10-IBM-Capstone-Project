package com.ibm.consulting.sim.shared.infrastructure.kafka;


import com.ibm.consulting.sim.shared.domain.kafka.KafkaInboxEntity;
import com.ibm.consulting.sim.shared.domain.kafka.KafkaInboxId;
import com.ibm.consulting.sim.shared.domain.kafka.KafkaInboxRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores consumer-side idempotency claims. The composite primary key of
 * consumer group and event ID allows each group to process an event once.
 */
@Repository
interface SpringDataKafkaInboxRepository
        extends JpaRepository<KafkaInboxEntity, KafkaInboxId> {

    @Modifying
    @Query(
            value = """
                INSERT INTO kafka_inbox
                (
                    consumer_group,
                    event_id,
                    event_type,
                    topic,
                    partition_no,
                    offset_no,
                    processed_at
                )
                VALUES
                (
                    :consumerGroup,
                    :eventId,
                    :eventType,
                    :topic,
                    :partition,
                    :offset,
                    NOW()
                )
                -- A conflict means this consumer group already claimed the event.
                ON CONFLICT
                (
                    consumer_group,
                    event_id
                )
                DO NOTHING
                """,
            nativeQuery = true
    )
    int insertIfAbsent(

            @Param("consumerGroup")
            String consumerGroup,

            @Param("eventId")
            UUID eventId,

            @Param("eventType")
            String eventType,

            @Param("topic")
            String topic,

            @Param("partition")
            int partition,

            @Param("offset")
            long offset
    );

    @Modifying
    @Query(value = """
            WITH expired AS (
                SELECT consumer_group, event_id
                FROM kafka_inbox
                WHERE processed_at < :cutoff
                ORDER BY processed_at
                LIMIT :limit
            )
            DELETE FROM kafka_inbox inbox
            USING expired
            WHERE inbox.consumer_group = expired.consumer_group
              AND inbox.event_id = expired.event_id
            """, nativeQuery = true)
    int deleteProcessedBefore(
            @Param("cutoff") Instant cutoff,
            @Param("limit") int limit
    );
}

@Repository
public class JPAKafkaInboxRepository implements KafkaInboxRepository {

    private final SpringDataKafkaInboxRepository repo;

    public JPAKafkaInboxRepository(SpringDataKafkaInboxRepository repo)
    {
        this.repo = repo;
    }

    /**
     * @param consumerGroup
     * @param eventId
     * @param eventType
     * @param topic
     * @param partition
     * @param offset
     * @return
     */
    @Override
    public int insertIfAbsent(String consumerGroup, UUID eventId, String eventType, String topic, int partition, long offset) {
        return repo.insertIfAbsent(consumerGroup,eventId,eventType,topic,partition,offset);
    }

    @Override
    public int deleteProcessedBefore(Instant cutoff, int limit) {
        Objects.requireNonNull(cutoff, "cutoff must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return repo.deleteProcessedBefore(cutoff, limit);
    }
}
