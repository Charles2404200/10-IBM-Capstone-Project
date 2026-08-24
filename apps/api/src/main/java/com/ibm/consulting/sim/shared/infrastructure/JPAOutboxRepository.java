package com.ibm.consulting.sim.shared.infrastructure;

import com.ibm.consulting.sim.shared.domain.OutboxEvent;
import com.ibm.consulting.sim.shared.domain.OutboxEventRepository;
import com.ibm.consulting.sim.shared.domain.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataOutboxRepository
        extends JpaRepository<OutboxEvent, UUID> {

    // Lock the rows I'm taking. If another
    // publisher has already locked a row,
    // don't wait for it—just skip it and
    // find another row.
    // for update skip locked rows
    // so it will only work
    // when the queries are happening in the database
    // transaction
    @Query(value = """
            SELECT o.*
            FROM event_outbox o

            WHERE o.status = 'PENDING'

            AND (
                o.ordering_mode = 'UNORDERED'

                OR NOT EXISTS (
                    SELECT 1
                    FROM event_outbox previous

                    WHERE previous.ordering_key = o.ordering_key
                      AND previous.sequence_number < o.sequence_number
                      AND previous.status <> 'PUBLISHED'
                )
            )

            ORDER BY o.created_at

            LIMIT :limit

            FOR UPDATE SKIP LOCKED
            """,
            nativeQuery = true)
    List<OutboxEvent> findDispatchableEvents(
            @Param("limit") int limit
    );

    // the UPDATE creates a lock
    // to lock the row which the query updates
    @Modifying
    @Query("""
                UPDATE OutboxEvent e
                SET e.status = :processingStatus
                WHERE e.id = :eventId
                  AND e.status = :pendingStatus
            """)
    int markProcessingIfPending(
            @Param("eventId") UUID eventId,
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("processingStatus") OutboxStatus processingStatus
    );

    // deleting published events
    // before a cutoff time
    @Modifying
        @Query("""
        DELETE FROM OutboxEvent e
        WHERE e.status = :status
          AND e.publishedAt < :cutoff
    """)
        int deletePublishedBefore(
                @Param("status") OutboxStatus status,
                @Param("cutoff") Instant cutoff
        );
}

@Repository
public class JPAOutboxRepository implements OutboxEventRepository {
    private SpringDataOutboxRepository repo;

    public JPAOutboxRepository(SpringDataOutboxRepository repo) {
        this.repo = repo;
    }

    /**
     * @param event
     */
    @Override
    public void markPublished(OutboxEvent event) {
        event.markPublished();
        repo.save(event);
    }

    /**
     * @param eventId
     */
    @Override
    public void markPublished(UUID eventId) {
        Optional<OutboxEvent> event = this.repo.findById(eventId);
        OutboxEvent e = event.get();
        e.markPublished();
        repo.save(e);
    }

    /**
     * @param event
     */
    @Override
    public void markPendingAgain(OutboxEvent event) {
        event.retry();
        repo.save(event);
    }

    /**
     * @param eventId
     */
    @Override
    public void markPendingAgain(UUID eventId) {
        Optional<OutboxEvent> event = this.repo.findById(eventId);
        OutboxEvent e = event.get();
        e.retry();
        repo.save(e);
    }

    /**
     * @return
     */
    @Override
    public OutboxEvent createOrderedEvent(
            String eventType,
            String orderingKey,
            int sequence,
            String payload,
            String topic,
            String dest
    ) {
        OutboxEvent event = OutboxEvent.ordered(
                eventType,
                orderingKey,
                sequence,
                payload,
                topic,
                dest
        );

        repo.save(event);

        return event;
    }

    /**
     * @return
     */
    @Override
    public OutboxEvent createUnOrderedEvent(
            String eventType,
            String payload,
            String topic,
            String dest
    ) {
        OutboxEvent event = OutboxEvent.unordered(
                eventType,
                payload,
                topic,
                dest
        );

        repo.save(event);

        return event;
    }

    /**
     * @param eventId
     * @return
     */
    @Override
    public Optional<OutboxEvent> findById(UUID eventId) {
        return repo.findById(eventId);
    }

    /**
     * @param events
     */
    @Override
    public List<OutboxEvent> findDispatchableEvents(int events) {
        return repo.findDispatchableEvents(events);
    }


    public int markProcessingIfPending(UUID eventId) {
        return this.repo.markProcessingIfPending(
                eventId,
                OutboxStatus.PENDING,
                OutboxStatus.PROCESSING
        );
    }

    @Override
    public void deletePublishedBefore(Instant time)
    {
        this.repo.deletePublishedBefore(
                OutboxStatus.PUBLISHED,
                time
        );
    }
}
