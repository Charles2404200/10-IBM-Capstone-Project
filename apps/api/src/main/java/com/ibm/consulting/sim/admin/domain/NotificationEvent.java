package com.ibm.consulting.sim.admin.domain;

import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "notification",
        // we have this event_id if
        // the application crashes
        // and the kafka offset (which
        // is the like location of the message
        // in the partition )is
        // not advanced properly
        // the consumer might pull
        // that same message and
        // might try to save the notification
        // twice in the DB which is not great
        // so we need to set the event_id
        // unique in order to prevent that
        // which is send from the producer side
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_notification_event_id",
                        columnNames = "event_id"
                )
        })
public class NotificationEvent extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    /** User-facing notification heading; unrelated to the Kafka topic name. */
    @Column(name = "topic_name", nullable = false, updatable = false, length = 160)
    private String topicName;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private UserRole role;

    public NotificationEvent() {}

    public static NotificationEvent create(
            UUID eventId,
            UUID userId,
            String topicName,
            String message,
            UserRole role) {
        NotificationEvent event = new NotificationEvent();
        event.eventId = eventId;
        event.userId = userId;
        event.topicName = topicName;
        event.message = message;
        event.role = role;
        return event;
    }

    public UUID getEventId() { return eventId ;}
    public UUID getUserId() { return userId; }
    public String getTopicName() { return topicName; }
    public String getMessage() { return message; }
    public UserRole getRole() { return role; }
}
