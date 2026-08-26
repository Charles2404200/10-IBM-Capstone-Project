package com.ibm.consulting.sim.shared.infrastructure;

import com.ibm.consulting.sim.shared.domain.outbox.OrderingMode;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEvent;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

interface SpringDataOutboxRepository
        extends JpaRepository<OutboxEvent, UUID> {

    /*
     * Hibernate interprets the JPA lock timeout value -2
     * as SKIP_LOCKED.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(
            name = "jakarta.persistence.lock.timeout",
            value = "-2"
    ))
    @Query("""
            SELECT candidate
            FROM OutboxEvent candidate
            WHERE candidate.status = :pendingStatus
              AND (
                    candidate.nextAttemptAt IS NULL
                    OR candidate.nextAttemptAt <= :dispatchTime
              )
              AND (
                    candidate.orderingMode = :unorderedMode
                    OR NOT EXISTS (
                        SELECT predecessor.id
                        FROM OutboxEvent predecessor
                        WHERE predecessor.orderingKey = candidate.orderingKey
                          AND predecessor.sequenceNumber < candidate.sequenceNumber
                          AND predecessor.status <> :publishedStatus
                    )
              )
            ORDER BY candidate.createdAt
            """)
    List<OutboxEvent> findDispatchableForUpdate(
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("publishedStatus") OutboxStatus publishedStatus,
            @Param("unorderedMode") OrderingMode unorderedMode,
            @Param("dispatchTime") Instant dispatchTime,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT outbox
            FROM OutboxEvent outbox
            WHERE outbox.id = :eventId
            """)
    Optional<OutboxEvent> findByIdForUpdate(
            @Param("eventId") UUID eventId
    );

    /*
     * Only the worker holding the current claim token
     * is allowed to mark this event as published.
     *
     * A stale worker using an old token will update 0 rows.
     */
    @Modifying
    @Query("""
            UPDATE OutboxEvent outbox
               SET outbox.status = :published,
                   outbox.publishedAt = :publishedAt,
                   outbox.processingStartedAt = NULL,
                   outbox.nextAttemptAt = NULL,
                   outbox.claimToken = NULL
             WHERE outbox.id = :eventId
               AND outbox.status = :processing
               AND outbox.claimToken = :claimToken
            """)
    int markPublishedIfOwned(
            @Param("eventId") UUID eventId,
            @Param("claimToken") UUID claimToken,
            @Param("processing") OutboxStatus processing,
            @Param("published") OutboxStatus published,
            @Param("publishedAt") Instant publishedAt
    );

    /*
     * Only the worker holding the current claim token
     * may return the event to PENDING.
     *
     * This prevents an old worker from changing the state
     * after another worker has reclaimed the event.
     */
    @Modifying
    @Query("""
            UPDATE OutboxEvent outbox
               SET outbox.status = :pending,
                   outbox.processingStartedAt = NULL,
                   outbox.nextAttemptAt = :nextAttemptAt,
                   outbox.attemptCount = outbox.attemptCount + 1,
                   outbox.claimToken = NULL
             WHERE outbox.id = :eventId
               AND outbox.status = :processing
               AND outbox.claimToken = :claimToken
            """)
    int markPendingAgainIfOwned(
            @Param("eventId") UUID eventId,
            @Param("claimToken") UUID claimToken,
            @Param("processing") OutboxStatus processing,
            @Param("pending") OutboxStatus pending,
            @Param("nextAttemptAt") Instant nextAttemptAt
    );

    /*
     * Recovery is different from a normal retry.
     *
     * The current claim has expired, therefore its token
     * must be removed before another worker can claim it.
     */
    @Modifying
    @Query("""
            UPDATE OutboxEvent outbox
               SET outbox.status = :pending,
                   outbox.processingStartedAt = NULL,
                   outbox.claimToken = NULL
             WHERE outbox.status = :processing
               AND outbox.processingStartedAt < :cutoff
            """)
    int recoverStaleProcessing(
            @Param("processing") OutboxStatus processing,
            @Param("pending") OutboxStatus pending,
            @Param("cutoff") Instant cutoff
    );

    @Modifying
    @Query("""
            DELETE FROM OutboxEvent outbox
            WHERE outbox.status = :status
              AND outbox.publishedAt < :cutoff
            """)
    int deletePublishedBefore(
            @Param("status") OutboxStatus status,
            @Param("cutoff") Instant cutoff
    );
}


@Repository
public class JPAOutboxRepository
        implements OutboxEventRepository {

    private final SpringDataOutboxRepository repository;

    public JPAOutboxRepository(
            SpringDataOutboxRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public void save(OutboxEvent event) {
        repository.save(
                Objects.requireNonNull(
                        event,
                        "event must not be null"
                )
        );
    }

    @Override
    public Optional<OutboxEvent> findById(UUID eventId) {
        return repository.findById(
                Objects.requireNonNull(
                        eventId,
                        "eventId must not be null"
                )
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<OutboxEvent> findByIdForUpdate(
            UUID eventId
    ) {
        return repository.findByIdForUpdate(
                Objects.requireNonNull(
                        eventId,
                        "eventId must not be null"
                )
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<OutboxEvent> findDispatchableForUpdate(
            int limit
    ) {

        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "limit must be positive"
            );
        }

        return repository.findDispatchableForUpdate(
                OutboxStatus.PENDING,
                OutboxStatus.PUBLISHED,
                OrderingMode.UNORDERED,
                Instant.now(),
                PageRequest.of(0, limit)
        );
    }

    @Override
    @Transactional
    public int markPublishedIfOwned(
            UUID eventId,
            UUID claimToken
    ) {

        Objects.requireNonNull(
                eventId,
                "eventId must not be null"
        );

        Objects.requireNonNull(
                claimToken,
                "claimToken must not be null"
        );

        return repository.markPublishedIfOwned(
                eventId,
                claimToken,
                OutboxStatus.PROCESSING,
                OutboxStatus.PUBLISHED,
                Instant.now()
        );
    }

    @Override
    @Transactional
    public int markPendingAgainIfOwned(
            UUID eventId,
            UUID claimToken,
            Instant nextAttemptAt
    ) {

        Objects.requireNonNull(
                eventId,
                "eventId must not be null"
        );

        Objects.requireNonNull(
                claimToken,
                "claimToken must not be null"
        );

        Objects.requireNonNull(
                nextAttemptAt,
                "nextAttemptAt must not be null"
        );

        return repository.markPendingAgainIfOwned(
                eventId,
                claimToken,
                OutboxStatus.PROCESSING,
                OutboxStatus.PENDING,
                nextAttemptAt
        );
    }

    @Override
    @Transactional
    public int recoverStaleProcessing(
            Instant cutoff
    ) {

        return repository.recoverStaleProcessing(
                OutboxStatus.PROCESSING,
                OutboxStatus.PENDING,
                Objects.requireNonNull(
                        cutoff,
                        "cutoff must not be null"
                )
        );
    }

    @Override
    @Transactional
    public void deletePublishedBefore(
            Instant cutoff
    ) {

        repository.deletePublishedBefore(
                OutboxStatus.PUBLISHED,
                Objects.requireNonNull(
                        cutoff,
                        "cutoff must not be null"
                )
        );
    }
}

//@Repository
//public class JPAOutboxRepository implements OutboxEventRepository {
//
//    private final SpringDataOutboxRepository repository;
//
//    public JPAOutboxRepository(SpringDataOutboxRepository repository) {
//        this.repository = repository;
//    }
//
//    @Override
//    public void save(OutboxEvent event) {
//        repository.save(Objects.requireNonNull(event, "event must not be null"));
//    }
//
//    @Override
//    public Optional<OutboxEvent> findById(UUID eventId) {
//        return repository.findById(
//                Objects.requireNonNull(eventId, "eventId must not be null")
//        );
//    }
//
//    @Override
//    @Transactional(propagation = Propagation.MANDATORY)
//    public Optional<OutboxEvent> findByIdForUpdate(UUID eventId) {
//        return repository.findByIdForUpdate(
//                Objects.requireNonNull(eventId, "eventId must not be null")
//        );
//    }
//
//    @Override
//    @Transactional(propagation = Propagation.MANDATORY)
//    public List<OutboxEvent> findDispatchableForUpdate(int limit) {
//        if (limit <= 0) {
//            throw new IllegalArgumentException("limit must be positive");
//        }
//
//        return repository.findDispatchableForUpdate(
//                OutboxStatus.PENDING,
//                OutboxStatus.PUBLISHED,
//                OrderingMode.UNORDERED,
//                Instant.now(),
//                PageRequest.of(0, limit)
//        );
//    }
//
//    @Override
//    @Transactional
//    public int recoverStaleProcessing(Instant cutoff) {
//        return repository.recoverStaleProcessing(
//                OutboxStatus.PROCESSING,
//                OutboxStatus.PENDING,
//                Objects.requireNonNull(cutoff, "cutoff must not be null")
//        );
//    }
//
//    @Override
//    @Transactional
//    public void deletePublishedBefore(Instant cutoff) {
//        repository.deletePublishedBefore(
//                OutboxStatus.PUBLISHED,
//                Objects.requireNonNull(cutoff, "cutoff must not be null")
//        );
//    }
//}
