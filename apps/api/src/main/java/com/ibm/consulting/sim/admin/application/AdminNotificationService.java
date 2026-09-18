package com.ibm.consulting.sim.admin.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.admin.domain.NotificationObject;
import com.ibm.consulting.sim.admin.domain.NotificationPriority;
import com.ibm.consulting.sim.admin.domain.NotifyUsersEvents;
import com.ibm.consulting.sim.admin.infrastructure.NotificationKafkaProperties;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.shared.domain.outbox.EventEnvelope;
import com.ibm.consulting.sim.shared.domain.outbox.EventSequenceRepository;
import com.ibm.consulting.sim.shared.domain.outbox.OrderingMode;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEvent;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AdminNotificationService {

    private static final Logger log =
            LoggerFactory.getLogger(AdminNotificationService.class);

    private static final int NOTIFICATION_SCHEMA_VERSION = 1;
    private static final String ORDERING_KEY_PREFIX = "notifications:";

    private final NotificationKafkaProperties properties;
    private final EventSequenceRepository sequenceRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public AdminNotificationService(
            NotificationKafkaProperties properties,
            EventSequenceRepository sequenceRepository,
            OutboxEventRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.sequenceRepository = sequenceRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public NotificationPublishResult notifyRoles(
            UUID userId,
            String topicName,
            String message,
            List<UserRole> roles
    ) {
        return notifyRoles(userId, topicName, message, roles, NotificationPriority.NORMAL);
    }

    @Transactional
    public NotificationPublishResult notifyRoles(
            UUID userId,
            String topicName,
            String message,
            List<UserRole> roles,
            NotificationPriority priority
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(topicName, "topicName must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(roles, "roles must not be null");
        NotificationPriority effectivePriority = NotificationPriority.normalize(priority);

        List<UserRole> distinctRoles = roles.stream()
                .map(role -> Objects.requireNonNull(
                        role,
                        "roles must not contain null"
                ))
                .distinct()
                .toList();

        if (distinctRoles.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one notification role is required"
            );
        }

        log.info(
                "Queueing notification batch: userId={}, roles={}, roleCount={}",
                userId,
                distinctRoles,
                distinctRoles.size()
        );

        distinctRoles.forEach(role -> publishForRoleOrdered(
                userId,
                topicName,
                message,
                role,
                effectivePriority
        ));

        NotificationPublishResult result = new NotificationPublishResult(
                distinctRoles.size(),
                distinctRoles
        );
        log.info(
                "Notification batch accepted: userId={}, roles={}, count={}",
                userId,
                distinctRoles,
                result.publishedCount()
        );
        return result;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    private void publishForRoleUnOrdered(
            UUID userId,
            String topicName,
            String message,
            UserRole role,
            NotificationPriority priority
    ){
        UUID eventId = UUID.randomUUID();
        String kafkaTopic = properties.topic().name();
        String eventType = NotifyUsersEvents.NOTIFICATION_PUBLISHED.name();

        NotificationObject notification = new NotificationObject(
                eventId,
                userId,
                topicName,
                message,
                role,
                priority
        );
        String payload = serialize(notification);

        EventEnvelope envelope = new EventEnvelope(
                eventId,
                eventType,
                NOTIFICATION_SCHEMA_VERSION,
                OrderingMode.UNORDERED,
                null,
                null,
                priority.toEventPriority(),
                Instant.now(),
                payload
        );

        log.debug(
                "Queueing unordered notification: eventId={}, userId={}, role={}, topic={}",
                eventId,
                userId,
                role,
                kafkaTopic
        );

        OutboxEvent outboxEvent = OutboxEvent.unordered(
                envelope.eventId(),
                kafkaTopic,
                envelope.eventType(),
                envelope.schemaVersion(),
                envelope.priority(),
                envelope.payload()
        );
        outboxRepository.save(outboxEvent);

        log.info(
                "Notification queued in outbox: eventId={}, eventType={}, topic={}",
                envelope.eventId(),
                envelope.eventType(),
                kafkaTopic
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    private void publishForRoleOrdered(
            UUID userId,
            String topicName,
            String message,
            UserRole role,
            NotificationPriority priority
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
                role,
                priority
        );
        String payload = serialize(notification);

        EventEnvelope envelope = new EventEnvelope(
                eventId,
                eventType,
                NOTIFICATION_SCHEMA_VERSION,
                OrderingMode.ORDERED,
                orderingKey,
                sequenceNumber,
                priority.toEventPriority(),
                Instant.now(),
                payload
        );

        log.debug(
                "Queueing ordered notification: eventId={}, userId={}, role={}, topic={}, sequence={}",
                eventId,
                userId,
                role,
                kafkaTopic,
                sequenceNumber
        );

        OutboxEvent outboxEvent = OutboxEvent.ordered(
                envelope.eventId(),
                kafkaTopic,
                envelope.eventType(),
                envelope.schemaVersion(),
                envelope.orderingKey(),
                envelope.sequenceNumber(),
                envelope.priority(),
                envelope.payload()
        );
        outboxRepository.save(outboxEvent);

        log.info(
                "Notification queued in outbox: eventId={}, eventType={}, topic={}",
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
