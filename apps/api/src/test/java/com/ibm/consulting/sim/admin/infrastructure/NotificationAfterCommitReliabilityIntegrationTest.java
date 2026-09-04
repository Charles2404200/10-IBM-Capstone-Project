package com.ibm.consulting.sim.admin.infrastructure;

import com.ibm.consulting.sim.admin.application.NotificationMetrics;
import com.ibm.consulting.sim.admin.application.NotificationPublishedHandler;
import com.ibm.consulting.sim.admin.application.NotificationQueryField;
import com.ibm.consulting.sim.admin.application.NotificationRealtimePublisher;
import com.ibm.consulting.sim.admin.domain.NotificationCentreRepository;
import com.ibm.consulting.sim.admin.domain.NotificationObject;
import com.ibm.consulting.sim.admin.domain.NotificationPriority;
import com.ibm.consulting.sim.admin.domain.NotificationRepository;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.shared.application.kafka.KafkaEventContext;
import com.ibm.consulting.sim.shared.domain.outbox.EventPriority;
import com.ibm.consulting.sim.shared.domain.outbox.OrderingMode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@DataJpaTest
@Import({
        JpaNotificationRepository.class,
        JpaNotificationCentreRepository.class,
        JpaNotificationReadRepository.class,
        NotificationPublishedHandler.class,
        NotificationRealtimePublisher.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationAfterCommitReliabilityIntegrationTest {

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
    private NotificationPublishedHandler handler;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private NotificationCentreRepository centreRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManager entityManager;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;
    @MockBean
    private NotificationMetrics metrics;

    @Test
    void websocketFailureAfterCommitCannotUndoNotificationAndRestReadModelCanRecoverIt() {
        UUID eventId = UUID.randomUUID();
        User producer = User.create(
                "notification-producer-" + UUID.randomUUID() + "@example.com",
                "not-used-by-this-test",
                "Notification producer",
                UserRole.ADMINISTRATOR);
        NotificationObject payload = new NotificationObject(
                eventId, producer.getId(), "Course published", "A new course is available.",
                UserRole.LEARNER, NotificationPriority.IMPORTANT);
        doThrow(new IllegalStateException("simulated socket outage"))
                .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // notification.user_id is a foreign key and must identify a real producer.
            entityManager.persist(producer);
            handler.handle(payload, context(eventId));
        });

        assertTrue(notificationRepository.existsByEventId(eventId));
        var page = centreRepository.findPage(
                UUID.randomUUID(), UserRole.LEARNER, null, 10,
                NotificationQueryField.defaults());
        assertEquals(1, page.stream()
                .filter(item -> eventId.equals(item.cursorEventId()))
                .count());
        verify(metrics).recordWebSocketFailure(NotificationPriority.IMPORTANT);
    }

    private KafkaEventContext context(UUID eventId) {
        return new KafkaEventContext(
                eventId, "notifications", 0, 1L, "notifications:LEARNER",
                NotificationPublishedHandler.EVENT_TYPE, 1, OrderingMode.ORDERED,
                "notifications:LEARNER", 1L, EventPriority.HIGH, Instant.now());
    }
}
