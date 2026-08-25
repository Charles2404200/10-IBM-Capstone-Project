package com.ibm.consulting.sim.admin.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.admin.domain.NotificationObject;
import com.ibm.consulting.sim.admin.domain.NotifyUsersEvents;
import com.ibm.consulting.sim.admin.infrastructure.NotificationKafkaProperties;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.shared.application.kafka.KafkaEventPublisher;
import com.ibm.consulting.sim.shared.domain.EventEnvelope;
import com.ibm.consulting.sim.shared.domain.EventSequenceRepository;
import com.ibm.consulting.sim.shared.domain.OrderingMode;
import com.ibm.consulting.sim.shared.domain.OutboxEvent;
import com.ibm.consulting.sim.shared.domain.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class AdminNotificationService {

    private static final Logger log =
            LoggerFactory.getLogger(AdminNotificationService.class);

    private static final int NOTIFICATION_SCHEMA_VERSION = 1;
    private static final String ORDERING_KEY_PREFIX = "notifications:";

    private final KafkaEventPublisher kafkaPublisher;
    private final NotificationKafkaProperties properties;
    private final EventSequenceRepository sequenceRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public AdminNotificationService(
            KafkaEventPublisher kafkaPublisher,
            NotificationKafkaProperties properties,
            EventSequenceRepository sequenceRepository,
            OutboxEventRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.kafkaPublisher = kafkaPublisher;
        this.properties = properties;
        this.sequenceRepository = sequenceRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<NotificationPublishResult> notifyRoles(
            UUID userId,
            String topicName,
            String message,
            List<UserRole> roles
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(topicName, "topicName must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(roles, "roles must not be null");

        List<UserRole> distinctRoles = roles.stream()
                .map(role -> Objects.requireNonNull(
                        role,
                        "roles must not contain null"
                ))
                .distinct()
                .toList();

        if (distinctRoles.isEmpty()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "At least one notification role is required"
                    )
            );
        }

        log.info(
                "Publishing notification batch: userId={}, roles={}, roleCount={}",
                userId,
                distinctRoles,
                distinctRoles.size()
        );

        List<CompletableFuture<Void>> publications = distinctRoles.stream()
                .map(role -> publishForRole(
                        userId,
                        topicName,
                        message,
                        role
                ))
                .toList();

        return CompletableFuture
                .allOf(publications.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> new NotificationPublishResult(
                        publications.size(),
                        distinctRoles
                ))
                .whenComplete((result, failure) -> {
                    if (failure == null) {
                        log.info(
                                "Notification batch accepted: userId={}, roles={}, count={}",
                                userId,
                                distinctRoles,
                                result.publishedCount()
                        );
                    } else {
                        log.error(
                                "Notification batch failed: userId={}, roles={}",
                                userId,
                                distinctRoles,
                                failure
                        );
                    }
                });
    }

    private CompletableFuture<Void> publishForRole(
            UUID userId,
            String topicName,
            String message,
            UserRole role
    ) {
        UUID eventId = UUID.randomUUID();
        String kafkaTopic = properties.topic().name();
        String eventType = NotifyUsersEvents.NOTIFICATION_PUBLISHED.name();
        String orderingKey = ORDERING_KEY_PREFIX + role.name();
        long sequenceNumber = sequenceRepository.next(orderingKey);

        NotificationObject notification = new NotificationObject(
                eventId,
                userId,
                topicName,
                message,
                role
        );
        String payload = serialize(notification);

        EventEnvelope envelope = new EventEnvelope(
                eventId,
                eventType,
                NOTIFICATION_SCHEMA_VERSION,
                OrderingMode.ORDERED,
                orderingKey,
                sequenceNumber,
                Instant.now(),
                payload
        );

        log.debug(
                "Publishing notification directly: eventId={}, userId={}, role={}, topic={}, sequence={}",
                eventId,
                userId,
                role,
                kafkaTopic,
                sequenceNumber
        );

        try {
            return kafkaPublisher.publish(kafkaTopic, envelope)
                    .handle((ignored, failure) -> {
                        if (failure == null) {
                            log.info(
                                    "Notification published directly: eventId={}, userId={}, role={}, topic={}",
                                    eventId,
                                    userId,
                                    role,
                                    kafkaTopic
                            );
                        } else {
                            saveFailedPublication(
                                    envelope,
                                    kafkaTopic,
                                    failure
                            );
                        }

                        return null;
                    });
        } catch (RuntimeException failure) {
            try {
                saveFailedPublication(envelope, kafkaTopic, failure);
                return CompletableFuture.completedFuture(null);
            } catch (RuntimeException outboxFailure) {
                outboxFailure.addSuppressed(failure);
                return CompletableFuture.failedFuture(outboxFailure);
            }
        }
    }

    private void saveFailedPublication(
            EventEnvelope envelope,
            String kafkaTopic,
            Throwable failure
    ) {
        log.warn(
                "Direct Kafka publication failed; saving to outbox: eventId={}, eventType={}, topic={}",
                envelope.eventId(),
                envelope.eventType(),
                kafkaTopic,
                failure
        );

        OutboxEvent outboxEvent = OutboxEvent.ordered(
                envelope.eventId(),
                kafkaTopic,
                envelope.eventType(),
                envelope.schemaVersion(),
                envelope.orderingKey(),
                envelope.sequenceNumber(),
                envelope.payload()
        );
        outboxRepository.save(outboxEvent);

        log.info(
                "Failed Kafka publication saved to outbox: eventId={}, eventType={}, topic={}",
                envelope.eventId(),
                envelope.eventType(),
                kafkaTopic
        );
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Cannot serialize notification payload",
                    exception
            );
        }
    }
}
