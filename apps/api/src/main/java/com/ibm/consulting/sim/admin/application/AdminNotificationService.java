package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.admin.domain.NotificationObject;
import com.ibm.consulting.sim.admin.domain.NotifyUsersEvents;
import com.ibm.consulting.sim.admin.infrastructure.NotificationKafkaProperties;
import com.ibm.consulting.sim.admin.infrastructure.realtime.NotificationWebSocketDestinations;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.shared.domain.EventEnvelope;
import com.ibm.consulting.sim.shared.domain.OutboxEvent;
import com.ibm.consulting.sim.shared.domain.OutboxEventRepository;
import com.ibm.consulting.sim.shared.infrastructure.JPAOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Service
public class AdminNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AdminNotificationService.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final NotificationKafkaProperties properties;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public AdminNotificationService(KafkaTemplate<String, Object> kafkaTemplate,
                                    NotificationKafkaProperties properties,
                                    OutboxEventRepository outboxRepository,
                                    ObjectMapper mapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = mapper;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Publishes the same message once for every distinct requested role. Sends are
     * started together and the returned future completes only after Kafka has
     * acknowledged every record.
     */
    @Transactional
    public CompletableFuture<NotificationPublishResult> notifyRoles(
            UUID userId, String message, List<UserRole> roles) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(roles, "roles must not be null");

        List<UserRole> distinctRoles = roles.stream()
                .map(role -> Objects.requireNonNull(role, "roles must not contain null"))
                .distinct()
                .toList();
        if (distinctRoles.isEmpty()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("At least one notification role is required"));
        }

        log.info("Publishing notification batch: userId={}, roles={}, roleCount={}",
                userId, distinctRoles, distinctRoles.size());
        List<CompletableFuture<SendResult<String, Object>>> sends = distinctRoles.stream()
                .map(role -> {
                    try {
                        return notifyUsers(UUID.randomUUID() , userId, message, role);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
        // for role there is a new randomly generated eventId

        return CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    // for some reason the return semantics don't work
                    // so the join is done so that return keyword semantics
                    // work in the codebase
                    // eventhough the join() for each of the Completable Future is redundant
                    sends.forEach(CompletableFuture::join);
                    return new NotificationPublishResult(sends.size(), distinctRoles);
                })
                .whenComplete((results, exception) -> {
                    if (exception != null) {
                        log.error("Notification batch publish failed: userId={}, roles={}",
                                userId, distinctRoles, exception);
                    } else {
                        log.info("Notification batch published: userId={}, roles={}, publishedCount={}",
                                userId, distinctRoles, results.publishedCount());
                    }
                });
    }

    /**
     * Publishes a notification for a role. The role is used as the Kafka key so
     * notifications for the same audience retain partition ordering.
     */
    private CompletableFuture<SendResult<String, Object>> notifyUsers(
            UUID eventId , UUID userId, String message, UserRole role) throws JsonProcessingException {
        NotificationObject notification = new NotificationObject(eventId , userId, message, role);
        String topic = properties.topic().name();

        log.debug("Publishing notification: eventId={} userId={}, role={}, topic={}",eventId, userId, role, topic);

        EventEnvelope envelope = new EventEnvelope(
                eventId,
                NotifyUsersEvents.NOTIFICATION_PUBLISHED.name(),
                eventId.toString(),
                NotifyUsersEvents.NOTIFICATION_PUBLISHED.ordinal(),
                NotificationWebSocketDestinations.subscriptionTopic(role),
                objectMapper.writeValueAsString(notification)
        );

        CompletableFuture<SendResult<String, Object>> result =
                kafkaTemplate.send(topic,envelope.orderingKey(), envelope);

        result.whenComplete((sendResult, exception) -> {
            if (exception != null) {
                log.error("Notification publish failed: eventId={} userId={}, role={}, topic={}",
                        eventId , userId, role, topic, exception);

                try {
                    // if it fails to publish it in the
                    // kafka then store it in the database
                    // just to not lose the message
                    this.outboxRepository.createUnOrderedEvent(
                            NotifyUsersEvents.NOTIFICATION_PUBLISHED.name(),
                            objectMapper.writeValueAsString(notification),
                            topic,
                            envelope.dest()
                    );
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                return;
            }
            log.info("Notification published: eventId={} userId={}, role={}, topic={}, partition={}, offset={}",
                    eventId , userId, role, topic,
                    sendResult.getRecordMetadata().partition(),
                    sendResult.getRecordMetadata().offset());
        });
        return result;
    }
}
