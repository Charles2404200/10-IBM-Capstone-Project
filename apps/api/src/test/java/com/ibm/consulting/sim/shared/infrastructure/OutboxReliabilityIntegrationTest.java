package com.ibm.consulting.sim.shared.infrastructure;

import com.ibm.consulting.sim.shared.application.outbox.OutboxClaimService;
import com.ibm.consulting.sim.shared.domain.outbox.EventPriority;
import com.ibm.consulting.sim.shared.domain.outbox.OrderingMode;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEvent;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises lease ownership, recovery, retry scheduling, cleanup, and
 * SKIP-LOCKED concurrency against PostgreSQL rather than an in-memory database.
 */
@DataJpaTest
@Import({JPAOutboxRepository.class, OutboxClaimService.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxReliabilityIntegrationTest {

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private OutboxClaimService claimService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private TransactionTemplate transaction;

    @BeforeEach
    void clearOutbox() {
        transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status ->
                entityManager.createQuery("DELETE FROM OutboxEvent").executeUpdate());
    }

    @Test
    void crashedClaimIsRecoveredAndOldWorkerCannotCompleteNewOwnership() {
        UUID firstToken = UUID.randomUUID();
        UUID secondToken = UUID.randomUUID();
        OutboxEvent original = OutboxEvent.ordered(
                UUID.randomUUID(),
                "notifications",
                "NOTIFICATION_PUBLISHED",
                1,
                "notifications:LEARNER",
                1,
                EventPriority.CRITICAL,
                "{\"message\":\"important\"}"
        );
        save(original);

        assertEquals(List.of(original.getId()), claimService.claimBatch(10, firstToken));
        OutboxSnapshot claimed = snapshot(original.getId());
        assertEquals(OutboxStatus.PROCESSING, claimed.status());
        assertEquals(firstToken, claimed.claimToken());
        assertNotNull(claimed.processingStartedAt());

        setProcessingStartedAt(original.getId(), Instant.now().minus(Duration.ofMinutes(10)));
        assertEquals(1, repository.recoverStaleProcessing(Instant.now().minus(Duration.ofMinutes(5))));

        OutboxSnapshot recovered = snapshot(original.getId());
        assertEquals(OutboxStatus.PENDING, recovered.status());
        assertNull(recovered.claimToken());
        assertNull(recovered.processingStartedAt());
        assertEquals(0, recovered.attemptCount(), "lease recovery is not proof of publish failure");

        assertEquals(List.of(original.getId()), claimService.claimBatch(10, secondToken));
        assertEquals(0, repository.markPublishedIfOwned(original.getId(), firstToken));
        assertEquals(1, repository.markPublishedIfOwned(original.getId(), secondToken));

        OutboxSnapshot published = snapshot(original.getId());
        assertEquals(OutboxStatus.PUBLISHED, published.status());
        assertNotNull(published.publishedAt());
        assertEquals(EventPriority.CRITICAL, published.priority());
        assertEquals(OrderingMode.ORDERED, published.orderingMode());
        assertEquals("notifications:LEARNER", published.orderingKey());
        assertEquals(1L, published.sequenceNumber());
    }

    @Test
    void recoveryChangesOnlyExpiredProcessingLeases() {
        OutboxEvent stale = processingEvent();
        OutboxEvent fresh = processingEvent();
        OutboxEvent pending = unordered();
        OutboxEvent published = publishedEvent();
        save(stale, fresh, pending, published);
        setProcessingStartedAt(stale.getId(), Instant.now().minus(Duration.ofMinutes(10)));

        assertEquals(1, repository.recoverStaleProcessing(Instant.now().minus(Duration.ofMinutes(5))));

        assertEquals(OutboxStatus.PENDING, snapshot(stale.getId()).status());
        assertEquals(OutboxStatus.PROCESSING, snapshot(fresh.getId()).status());
        assertEquals(OutboxStatus.PENDING, snapshot(pending.getId()).status());
        assertEquals(OutboxStatus.PUBLISHED, snapshot(published.getId()).status());
    }

    @Test
    void publicationFailureIncrementsAttemptAndPreventsHotRetry() {
        OutboxEvent event = unordered();
        UUID claimToken = UUID.randomUUID();
        save(event);
        assertEquals(List.of(event.getId()), claimService.claimBatch(10, claimToken));
        Instant retryAt = Instant.now().plus(Duration.ofMinutes(5));

        assertEquals(1, repository.markPendingAgainIfOwned(
                event.getId(), claimToken, retryAt));

        OutboxSnapshot retry = snapshot(event.getId());
        assertEquals(OutboxStatus.PENDING, retry.status());
        assertEquals(1, retry.attemptCount());
        assertNotNull(retry.nextAttemptAt());
        assertTrue(retry.nextAttemptAt().isAfter(Instant.now().plus(Duration.ofMinutes(4))));
        assertTrue(claimService.claimBatch(10, UUID.randomUUID()).isEmpty());
    }

    @Test
    void terminalFailureCannotBeReclaimedOrDeletedByPublishedCleanup() {
        String orderingKey = "failed-order:" + UUID.randomUUID();
        OutboxEvent event = OutboxEvent.ordered(
                UUID.randomUUID(), "events", "FIRST", 1, orderingKey, 1, "{}");
        OutboxEvent successor = OutboxEvent.ordered(
                UUID.randomUUID(), "events", "SECOND", 1, orderingKey, 2, "{}");
        UUID claimToken = UUID.randomUUID();
        save(event, successor);
        assertEquals(List.of(event.getId()), claimService.claimBatch(10, claimToken));

        assertEquals(1, repository.markFailedIfOwned(
                event.getId(), claimToken, "org.apache.kafka.common.KafkaException"));

        OutboxSnapshot failed = snapshot(event.getId());
        assertEquals(OutboxStatus.FAILED, failed.status());
        assertEquals(1, failed.attemptCount());
        assertNull(failed.claimToken());
        assertNull(failed.processingStartedAt());
        assertNull(failed.nextAttemptAt());
        assertNotNull(failed.failedAt());
        assertEquals("org.apache.kafka.common.KafkaException", failed.lastError());
        assertTrue(claimService.claimBatch(10, UUID.randomUUID()).isEmpty());
        assertEquals(0, repository.deletePublishedBefore(Instant.now().plus(Duration.ofDays(1)), 10));
        assertTrue(repository.findById(event.getId()).isPresent());
        assertEquals(OutboxStatus.PENDING, snapshot(successor.getId()).status());
    }

    @Test
    void concurrentDispatchersClaimDisjointRowsUsingDatabaseLocks() throws Exception {
        OutboxEvent[] events = new OutboxEvent[20];
        for (int index = 0; index < events.length; index++) {
            events[index] = unordered();
        }
        save(events);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> claimConcurrently(ready, start));
            var second = executor.submit(() -> claimConcurrently(ready, start));
            ready.await();
            start.countDown();

            List<UUID> firstClaim = first.get();
            List<UUID> secondClaim = second.get();
            HashSet<UUID> unique = new HashSet<>(firstClaim);
            assertTrue(
                    java.util.Collections.disjoint(Set.copyOf(firstClaim), secondClaim),
                    "dispatchers must never claim the same row"
            );
            unique.addAll(secondClaim);
            assertEquals(20, unique.size());
            assertEquals(10, firstClaim.size());
            assertEquals(10, secondClaim.size());
        }
    }

    @Test
    void cleanupDeletesOnlyExpiredPublishedRowsAndRespectsBatchSize() {
        OutboxEvent[] expired = {
                publishedEvent(), publishedEvent(), publishedEvent(), publishedEvent(), publishedEvent()
        };
        OutboxEvent recent = publishedEvent();
        OutboxEvent oldPending = unordered();
        OutboxEvent oldProcessing = processingEvent();
        save(expired[0], expired[1], expired[2], expired[3], expired[4],
                recent, oldPending, oldProcessing);

        Instant old = Instant.now().minus(Duration.ofDays(30));
        for (OutboxEvent event : expired) {
            setPublishedAt(event.getId(), old);
        }
        setCreatedAt(oldPending.getId(), old);
        setProcessingStartedAt(oldProcessing.getId(), old);
        Instant cutoff = Instant.now().minus(Duration.ofDays(2));

        assertEquals(2, repository.deletePublishedBefore(cutoff, 2));
        assertEquals(3, countExpiredPublished(cutoff));
        assertEquals(2, repository.deletePublishedBefore(cutoff, 2));
        assertEquals(1, repository.deletePublishedBefore(cutoff, 2));
        assertEquals(0, repository.deletePublishedBefore(cutoff, 2));

        assertTrue(repository.findById(recent.getId()).isPresent());
        assertTrue(repository.findById(oldPending.getId()).isPresent());
        assertTrue(repository.findById(oldProcessing.getId()).isPresent());
    }

    @Test
    void concurrentCleanupWorkersDeleteDisjointBatches() throws Exception {
        OutboxEvent[] expired = new OutboxEvent[20];
        Instant old = Instant.now().minus(Duration.ofDays(30));
        for (int index = 0; index < expired.length; index++) {
            expired[index] = publishedEvent();
        }
        save(expired);
        for (OutboxEvent event : expired) {
            setPublishedAt(event.getId(), old);
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(2));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> cleanupConcurrently(ready, start, cutoff));
            var second = executor.submit(() -> cleanupConcurrently(ready, start, cutoff));
            ready.await();
            start.countDown();

            assertEquals(20, first.get() + second.get());
            assertEquals(0, countExpiredPublished(cutoff));
        }
    }

    @Test
    void databaseRejectsProcessingStateWithoutLeaseMetadata() {
        OutboxEvent event = unordered();
        save(event);

        assertThrows(ConstraintViolationException.class, () ->
                transaction.executeWithoutResult(status -> {
                    entityManager.createNativeQuery("""
                            UPDATE event_outbox
                            SET status = 'PROCESSING', claim_token = NULL,
                                processing_started_at = NULL
                            WHERE id = :id
                            """)
                            .setParameter("id", event.getId())
                            .executeUpdate();
                    entityManager.flush();
                }));
    }

    private List<UUID> claimConcurrently(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        return claimService.claimBatch(10, UUID.randomUUID());
    }

    private int cleanupConcurrently(
            CountDownLatch ready,
            CountDownLatch start,
            Instant cutoff) throws InterruptedException {
        ready.countDown();
        start.await();
        return repository.deletePublishedBefore(cutoff, 10);
    }

    private void save(OutboxEvent... events) {
        transaction.executeWithoutResult(status -> {
            for (OutboxEvent event : events) {
                repository.save(event);
            }
        });
    }

    private OutboxEvent unordered() {
        return OutboxEvent.unordered(
                UUID.randomUUID(), "events", "TEST_EVENT", 1,
                EventPriority.NORMAL, "{}"
        );
    }

    private OutboxEvent processingEvent() {
        OutboxEvent event = unordered();
        event.markProcessing(UUID.randomUUID());
        return event;
    }

    private OutboxEvent publishedEvent() {
        OutboxEvent event = unordered();
        event.markProcessing(UUID.randomUUID());
        event.markPublished();
        return event;
    }

    private OutboxSnapshot snapshot(UUID eventId) {
        return transaction.execute(status -> repository.findById(eventId)
                .map(event -> new OutboxSnapshot(
                        event.getStatus(),
                        event.getClaimToken(),
                        event.getProcessingStartedAt(),
                        event.getNextAttemptAt(),
                        event.getPublishedAt(),
                        event.getFailedAt(),
                        event.getLastError(),
                        event.getAttemptCount(),
                        event.getEventPriority(),
                        event.getOrderingMode(),
                        event.getOrderingKey(),
                        event.getSequenceNumber()))
                .orElseThrow());
    }

    private void setProcessingStartedAt(UUID eventId, Instant value) {
        updateTimestamp("processingStartedAt", eventId, value);
    }

    private void setPublishedAt(UUID eventId, Instant value) {
        updateTimestamp("publishedAt", eventId, value);
    }

    private void setCreatedAt(UUID eventId, Instant value) {
        updateTimestamp("createdAt", eventId, value);
    }

    private void updateTimestamp(String property, UUID eventId, Instant value) {
        transaction.executeWithoutResult(status -> entityManager.createQuery(
                        "UPDATE OutboxEvent event SET event." + property + " = :value WHERE event.id = :id")
                .setParameter("value", value)
                .setParameter("id", eventId)
                .executeUpdate());
    }

    private long countExpiredPublished(Instant cutoff) {
        return transaction.execute(status -> entityManager.createQuery("""
                        SELECT COUNT(event)
                        FROM OutboxEvent event
                        WHERE event.status = :status AND event.publishedAt < :cutoff
                        """, Long.class)
                .setParameter("status", OutboxStatus.PUBLISHED)
                .setParameter("cutoff", cutoff)
                .getSingleResult());
    }

    private record OutboxSnapshot(
            OutboxStatus status,
            UUID claimToken,
            Instant processingStartedAt,
            Instant nextAttemptAt,
            Instant publishedAt,
            Instant failedAt,
            String lastError,
            int attemptCount,
            EventPriority priority,
            OrderingMode orderingMode,
            String orderingKey,
            Long sequenceNumber) {
    }
}
