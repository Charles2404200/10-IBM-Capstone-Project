package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.admin.domain.NotificationObject;
import com.ibm.consulting.sim.admin.infrastructure.realtime.NotificationWebSocketDestinations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationRealtimePublisher {

    private static final Logger log =
            LoggerFactory.getLogger(
                    NotificationRealtimePublisher.class
            );

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationMetrics metrics;

    public NotificationRealtimePublisher(
            SimpMessagingTemplate messagingTemplate,
            NotificationMetrics metrics) {
        this.messagingTemplate = messagingTemplate;
        this.metrics = metrics;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(NotificationPersistedEvent event) {
        NotificationObject notification = event.notification();
        NotificationRealtimeSummary summary = NotificationRealtimeSummary.from(notification);
        String destination = NotificationWebSocketDestinations
                .subscriptionTopic(notification.getRole());

        try {
            messagingTemplate.convertAndSend(destination, summary);
            metrics.recordWebSocketSent(notification.getPriority());
        } catch (Exception exception) {
            /*
             * The database remains the source of truth, and clients catch up
             * through GET /api/v1/notifications. Do not log the message body.
             */
            metrics.recordWebSocketFailure(notification.getPriority());
            log.warn(
                    "WebSocket notification delivery failed: eventId={}, role={}",
                    notification.getEventId(),
                    notification.getRole(),
                    exception
            );
        }
    }
}
