package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.admin.domain.NotificationEvent;
import com.ibm.consulting.sim.admin.domain.NotificationObject;
import com.ibm.consulting.sim.admin.domain.NotificationRepository;
import com.ibm.consulting.sim.shared.application.kafka.KafkaEventContext;
import com.ibm.consulting.sim.shared.application.kafka.KafkaEventHandler;
import com.ibm.consulting.sim.shared.domain.kafka.InvalidKafkaEventException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class NotificationPublishedHandler
        implements KafkaEventHandler<NotificationObject> {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationPublishedHandler.class);

    public static final String EVENT_TYPE =
            "NOTIFICATION_PUBLISHED";

    private final NotificationRepository notificationRepository;

    private final ApplicationEventPublisher applicationEventPublisher;

    public NotificationPublishedHandler(
            NotificationRepository notificationRepository,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.notificationRepository = notificationRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public Class<NotificationObject> payloadType() {
        return NotificationObject.class;
    }

    @Override
    public void handle(
            NotificationObject notification,
            KafkaEventContext context
    ) {

        log.info(
                "Handling Kafka event: eventType={}, eventId={}, topic={}, partition={}, offset={}",
                EVENT_TYPE,
                context.eventId(),
                context.topic(),
                context.partition(),
                context.offset()
        );

        /*
         * Validate envelope/payload consistency.
         */
        if (!context.eventId()
                .equals(notification.getEventId())) {

            log.error(
                    "Kafka eventId mismatch: envelopeEventId={}, payloadEventId={}, topic={}, partition={}, offset={}",
                    context.eventId(),
                    notification.getEventId(),
                    context.topic(),
                    context.partition(),
                    context.offset()
            );

            throw new InvalidKafkaEventException(
                    "Envelope eventId differs from notification eventId"
            );
        }

        log.debug(
                "Kafka event validated: eventId={}, userId={}, role={}",
                context.eventId(),
                notification.getUserId(),
                notification.getRole()
        );

        NotificationEvent entity =
                NotificationEvent.create(
                        context.eventId(),
                        notification.getUserId(),
                        notification.getTopicName(),
                        notification.getMessage(),
                        notification.getRole()
                );

        notificationRepository.save(entity);

        log.info(
                "Notification persisted: eventId={}, userId={}, role={}, destination={}",
                context.eventId(),
                notification.getUserId(),
                notification.getRole(),
                notification.getTopicName()
        );

        /*
         * Publish internal Spring event.
         */
        applicationEventPublisher.publishEvent(
                new NotificationPersistedEvent(
                        notification
                )
        );

        log.debug(
                "NotificationPersistedEvent published: eventId={}, userId={}",
                context.eventId(),
                notification.getUserId()
        );
    }
}