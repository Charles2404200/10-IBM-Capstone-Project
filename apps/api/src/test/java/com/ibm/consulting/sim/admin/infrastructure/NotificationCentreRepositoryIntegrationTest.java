package com.ibm.consulting.sim.admin.infrastructure;

import com.ibm.consulting.sim.admin.application.NotificationQueryField;
import com.ibm.consulting.sim.admin.application.NotificationCursor;
import com.ibm.consulting.sim.admin.domain.NotificationCentreRepository;
import com.ibm.consulting.sim.admin.domain.NotificationEvent;
import com.ibm.consulting.sim.admin.domain.NotificationPriority;
import com.ibm.consulting.sim.admin.domain.NotificationRead;
import com.ibm.consulting.sim.admin.domain.NotificationReadRepository;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.UUID;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({JpaNotificationCentreRepository.class, JpaNotificationReadRepository.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class NotificationCentreRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private NotificationCentreRepository centreRepository;

    @Autowired
    private NotificationReadRepository readRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void summaryProjectionAvoidsFullBodyAndEnforcesRoleVisibility() {
        User producer = User.create(
                "producer@example.com", "hash", "Producer", UserRole.ADMINISTRATOR);
        entityManager.persist(producer);
        NotificationEvent learner = NotificationEvent.create(
                UUID.randomUUID(), producer.getId(), "Course", "x".repeat(1_000),
                UserRole.LEARNER, NotificationPriority.IMPORTANT);
        NotificationEvent reviewer = NotificationEvent.create(
                UUID.randomUUID(), producer.getId(), "Private reviewer notice", "secret",
                UserRole.REVIEWER, NotificationPriority.NORMAL);
        entityManager.persist(learner);
        entityManager.persist(reviewer);
        entityManager.flush();

        User recipient = User.create(
                "recipient@example.com", "hash", "Recipient", UserRole.LEARNER);
        User inactiveRecipient = User.create(
                "inactive-recipient@example.com", "hash", "Inactive", UserRole.LEARNER);
        inactiveRecipient.deactivate();
        entityManager.persist(recipient);
        entityManager.persist(inactiveRecipient);
        entityManager.flush();
        UUID recipientId = recipient.getId();
        var page = centreRepository.findPage(
                recipientId, UserRole.LEARNER, null, 10,
                Set.of(NotificationQueryField.EVENT_ID, NotificationQueryField.TOPIC_NAME));

        assertEquals(1, page.size());
        assertEquals(Set.of("eventId", "topicName"), page.getFirst().fields().keySet());
        assertFalse(page.getFirst().fields().containsKey("message"));
        assertTrue(centreRepository.findDetail(learner.getEventId(), UserRole.LEARNER).isPresent());
        assertTrue(centreRepository.findDetail(reviewer.getEventId(), UserRole.LEARNER).isEmpty());
        assertEquals(1, centreRepository.countUnread(recipientId, UserRole.LEARNER));
        var unreadAudience = centreRepository.countAudienceReadStatus(
                learner.getId(), UserRole.LEARNER);
        assertEquals(1, unreadAudience.recipientCount());
        assertEquals(0, unreadAudience.readCount());
        var audiencePage = centreRepository.findAudienceReadStatusPage(
                learner.getId(), UserRole.LEARNER, null, 10);
        assertEquals(1, audiencePage.size());
        assertEquals(recipientId, audiencePage.getFirst().userId());
        assertFalse(audiencePage.getFirst().read());

        assertTrue(readRepository.createReadNotificationForUser(
                learner.getId(), recipientId, UserRole.LEARNER));
        entityManager.flush();
        assertEquals(0, centreRepository.countUnread(recipientId, UserRole.LEARNER));
        var readAudience = centreRepository.countAudienceReadStatus(
                learner.getId(), UserRole.LEARNER);
        assertEquals(1, readAudience.readCount());
        assertTrue(centreRepository.findAudienceReadStatusPage(
                learner.getId(), UserRole.LEARNER, null, 10).getFirst().read());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void twentyConcurrentReadAttemptsCreateExactlyOneReceipt() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        ReadTarget target = transaction.execute(status -> {
            User producer = User.create(
                    "concurrent-producer@example.com", "hash", "Producer", UserRole.ADMINISTRATOR);
            User recipient = User.create(
                    "concurrent-recipient@example.com", "hash", "Recipient", UserRole.LEARNER);
            entityManager.persist(producer);
            entityManager.persist(recipient);
            NotificationEvent notification = NotificationEvent.create(
                    UUID.randomUUID(), producer.getId(), "Concurrent", "Message", UserRole.LEARNER);
            entityManager.persist(notification);
            entityManager.flush();
            return new ReadTarget(notification.getId(), producer.getId(), recipient.getId());
        });

        try {
            try (var executor = Executors.newFixedThreadPool(8)) {
                List<Callable<Boolean>> attempts = new ArrayList<>();
                for (int index = 0; index < 20; index++) {
                    attempts.add(() -> transaction.execute(status ->
                            readRepository.createReadNotificationForUser(
                                    target.notificationId(), target.recipientId(), UserRole.LEARNER)));
                }
                List<Future<Boolean>> results = executor.invokeAll(attempts);
                assertEquals(1, results.stream().filter(future -> {
                    try {
                        return future.get();
                    } catch (Exception exception) {
                        throw new AssertionError(exception);
                    }
                }).count());
            }

            List<NotificationRead> rows = transaction.execute(status ->
                    readRepository.findByNotificationId(target.notificationId()));
            assertEquals(1, rows.size());
        } finally {
            // This test commits work from multiple threads and therefore cannot
            // rely on DataJpaTest's usual rollback. Always remove its fixtures so
            // randomized test order cannot pollute another projection test.
            transaction.executeWithoutResult(status -> {
                entityManager.createNativeQuery(
                                "DELETE FROM notification_reads WHERE notification_id = :notificationId")
                        .setParameter("notificationId", target.notificationId())
                        .executeUpdate();
                entityManager.createNativeQuery("DELETE FROM notification WHERE id = :notificationId")
                        .setParameter("notificationId", target.notificationId())
                        .executeUpdate();
                entityManager.createNativeQuery(
                                "DELETE FROM users WHERE id IN (:producerId, :recipientId)")
                        .setParameter("producerId", target.producerId())
                        .setParameter("recipientId", target.recipientId())
                        .executeUpdate();
            });
        }
    }

    @Test
    void differentUsersCanIndependentlyReadTheSameRoleNotification() {
        User producer = User.create(
                "multi-producer@example.com", "hash", "Producer", UserRole.ADMINISTRATOR);
        User first = User.create(
                "multi-first@example.com", "hash", "First", UserRole.LEARNER);
        User second = User.create(
                "multi-second@example.com", "hash", "Second", UserRole.LEARNER);
        entityManager.persist(producer);
        entityManager.persist(first);
        entityManager.persist(second);
        NotificationEvent notification = NotificationEvent.create(
                UUID.randomUUID(), producer.getId(), "Shared", "Message", UserRole.LEARNER);
        entityManager.persist(notification);
        entityManager.flush();

        assertTrue(readRepository.createReadNotificationForUser(
                notification.getId(), first.getId(), UserRole.LEARNER));
        assertTrue(readRepository.createReadNotificationForUser(
                notification.getId(), second.getId(), UserRole.LEARNER));
        entityManager.flush();

        assertEquals(2, readRepository.findByNotificationId(notification.getId()).size());
    }

    @Test
    void keysetPaginationHandlesEqualTimestampsAndConcurrentNewerInsertWithoutDuplicates() {
        User producer = User.create(
                "paging-producer@example.com", "hash", "Producer", UserRole.ADMINISTRATOR);
        User recipient = User.create(
                "paging-recipient@example.com", "hash", "Recipient", UserRole.REVIEWER);
        entityManager.persist(producer);
        entityManager.persist(recipient);
        List<NotificationEvent> original = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            NotificationEvent notification = NotificationEvent.create(
                    UUID.randomUUID(), producer.getId(), "Page " + index, "Message",
                    UserRole.REVIEWER);
            original.add(notification);
            entityManager.persist(notification);
        }
        entityManager.flush();
        Instant sameCreatedAt = Instant.parse("2026-09-01T00:00:00Z");
        entityManager.createQuery("""
                        UPDATE NotificationEvent notification
                           SET notification.createdAt = :createdAt
                         WHERE notification.role = :role
                        """)
                .setParameter("createdAt", sameCreatedAt)
                .setParameter("role", UserRole.REVIEWER)
                .executeUpdate();
        entityManager.clear();

        var first = centreRepository.findPage(
                recipient.getId(), UserRole.REVIEWER, null, 2,
                NotificationQueryField.defaults());
        assertEquals(2, first.size());
        var last = first.getLast();

        NotificationEvent newer = NotificationEvent.create(
                UUID.randomUUID(), producer.getId(), "New while paging", "Message",
                UserRole.REVIEWER);
        entityManager.persist(newer);
        entityManager.flush();

        var remaining = centreRepository.findPage(
                recipient.getId(), UserRole.REVIEWER,
                new NotificationCursor(last.cursorCreatedAt(), last.cursorEventId()),
                10, NotificationQueryField.defaults());
        Set<UUID> combined = new java.util.HashSet<>();
        first.forEach(item -> assertTrue(combined.add(item.cursorEventId())));
        remaining.forEach(item -> assertTrue(combined.add(item.cursorEventId())));

        assertEquals(original.stream().map(NotificationEvent::getEventId).collect(java.util.stream.Collectors.toSet()),
                combined);
        assertFalse(combined.contains(newer.getEventId()));
    }

    private record ReadTarget(UUID notificationId, UUID producerId, UUID recipientId) {
    }
}
