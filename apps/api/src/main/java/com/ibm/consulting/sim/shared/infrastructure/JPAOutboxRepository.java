package com.ibm.consulting.sim.shared.infrastructure;

import com.ibm.consulting.sim.shared.domain.OutboxEvent;
import com.ibm.consulting.sim.shared.domain.OutboxEventRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}

public class JPAOutboxRepository implements OutboxEventRepository
{
    private SpringDataOutboxRepository repo;

    public JPAOutboxRepository(SpringDataOutboxRepository repo)
    {
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
     * @param event
     */
    @Override
    public void markPendingAgain(OutboxEvent event) {
        event.retry();
        repo.save(event);
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


}
