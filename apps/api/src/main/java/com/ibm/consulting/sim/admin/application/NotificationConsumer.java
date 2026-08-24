package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.admin.domain.NotificationEvent;
import com.ibm.consulting.sim.admin.domain.NotificationObject;
import com.ibm.consulting.sim.admin.domain.NotificationRepository;
import com.ibm.consulting.sim.admin.infrastructure.realtime.NotificationWebSocketDestinations;
import com.ibm.consulting.sim.shared.domain.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public NotificationConsumer(NotificationRepository notificationRepository,
                                SimpMessagingTemplate messagingTemplate,
                                ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${app.kafka.notifications.topic.name}",
            groupId = "${app.kafka.notifications.consumer.group-id}",
            concurrency = "${app.kafka.notifications.consumer.concurrency}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            EventEnvelope envelope,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition
    ) throws IOException {
        NotificationObject notification;

        try
        {
            notification =
                    objectMapper.readValue(
                            envelope.payload(),
                            NotificationObject.class
                    );
        }
        catch(RuntimeException | IOException e)
        {
            log.error(
                    "Error while parsing: " + envelope.payload()
            );

            throw e;
        }


        UUID eventId = notification.getEventId();

        log.debug(
                "Received notification: eventId={}, userId={}, role={}, partition={}",
                eventId,
                notification.getUserId(),
                notification.getRole(),
                partition
        );

        // Kafka may redeliver the same event.
        if (notificationRepository.existsByEventId(eventId)) {

            log.info(
                    "Skipping already processed notification: eventId={}, userId={}, partition={}",
                    eventId,
                    notification.getUserId(),
                    partition
            );

            return;
        }

//        String destination =
//                NotificationWebSocketDestinations.subscriptionTopic(
//                        notification.getRole()
//                );

        try {

            /*
             * saveAndFlush instead of only save:
             *
             * We want the INSERT / UNIQUE(event_id) check to happen
             * before publishing the WebSocket notification.
             */
            notificationRepository.saveAndFlush(
                    NotificationEvent.create(
                            notification.getEventId(),
                            notification.getUserId(),
                            notification.getTopicName(),
                            notification.getMessage(),
                            notification.getRole()
                    )
            );

            messagingTemplate.convertAndSend(
                    envelope.dest(),
                    notification
            );

            log.info(
                    "Notification persisted and published: eventId={}, userId={}, role={}, partition={}, destination={}",
                    eventId,
                    notification.getUserId(),
                    notification.getRole(),
                    partition,
                    envelope.dest()
            );

        } catch (RuntimeException exception) {

            log.error(
                    "Notification processing failed: eventId={}, userId={}, role={}, partition={}",
                    eventId,
                    notification.getUserId(),
                    notification.getRole(),
                    partition,
                    exception
            );

            throw exception;
        }
    }
}
