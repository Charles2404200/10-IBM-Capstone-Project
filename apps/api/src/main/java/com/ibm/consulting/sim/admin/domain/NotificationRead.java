package com.ibm.consulting.sim.admin.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** Per-user acknowledgement for a role-targeted notification. */
@Entity
@Table(name = "notification_reads",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_notification_reads_notification_user",
                columnNames = {"notification_id", "user_id"}))
public class NotificationRead extends BaseEntity {

    @Column(name = "notification_id", nullable = false, updatable = false)
    private UUID notificationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "read_at", nullable = false, updatable = false)
    private Instant readAt;

    protected NotificationRead() {}

    public static NotificationRead create(
            UUID notificationId,
            UUID userId,
            Instant readAt) {
        NotificationRead notificationRead = new NotificationRead();
        notificationRead.notificationId = notificationId;
        notificationRead.userId = userId;
        notificationRead.readAt = readAt;
        return notificationRead;
    }

    public UUID getNotificationId() { return notificationId; }
    public UUID getUserId() { return userId; }
    public Instant getReadAt() { return readAt; }
}
