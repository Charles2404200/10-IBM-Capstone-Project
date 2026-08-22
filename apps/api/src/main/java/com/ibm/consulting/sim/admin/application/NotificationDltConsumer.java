package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.admin.domain.NotificationObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class NotificationDltConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationDltConsumer.class);

    @KafkaListener(
            topics = "${app.kafka.notifications.dlt.topic-name}",
            groupId = "${app.kafka.notifications.dlt.consumer.group-id}",
            concurrency = "${app.kafka.notifications.dlt.consumer.concurrency}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeDlt(
            NotificationObject notification,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition
    ) {

        log.error(
                "Notification found in DLT: eventId={}, userId={}, role={}, partition={}",
                notification.getEventId(),
                notification.getUserId(),
                notification.getRole(),
                partition
        );

        // Later:
        // - send alert
        // - store failure for investigation
        // - expose metric
        // - manually/reliably reprocess if needed
    }
}