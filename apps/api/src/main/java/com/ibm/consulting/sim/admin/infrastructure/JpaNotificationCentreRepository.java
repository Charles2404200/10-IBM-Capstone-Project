package com.ibm.consulting.sim.admin.infrastructure;

import com.ibm.consulting.sim.admin.application.NotificationCursor;
import com.ibm.consulting.sim.admin.application.NotificationQueryField;
import com.ibm.consulting.sim.admin.application.NotificationSharedDetail;
import com.ibm.consulting.sim.admin.application.ProjectedNotification;
import com.ibm.consulting.sim.admin.domain.NotificationCentreRepository;
import com.ibm.consulting.sim.admin.domain.NotificationEvent;
import com.ibm.consulting.sim.admin.domain.NotificationPriority;
import com.ibm.consulting.sim.admin.domain.NotificationRead;
import com.ibm.consulting.sim.identity.domain.UserRole;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Closed projection for notification list queries. The full message is
 * intentionally absent so list requests cannot accidentally load it.
 */
interface NotificationListJpaProjection {
    UUID getNotificationId();
    UUID getEventId();
    String getTopicName();
    String getMessagePreview();
    NotificationPriority getPriority();
    Instant getCreatedAt();
}

/** Spring Data query API kept separate from the domain repository adapter. */
@Repository
interface SpringDataNotificationCentreRepository extends JpaRepository<NotificationEvent, UUID> {

    @Query("""
            SELECT n.id AS notificationId,
                   n.eventId AS eventId,
                   n.topicName AS topicName,
                   n.messagePreview AS messagePreview,
                   n.priority AS priority,
                   n.createdAt AS createdAt
            FROM NotificationEvent n
            WHERE n.role = :role
            ORDER BY n.createdAt DESC, n.eventId DESC
            """)
    List<NotificationListJpaProjection> findFirstPage(
            @Param("role") UserRole role,
            Pageable pageable);

    @Query("""
            SELECT n.id AS notificationId,
                   n.eventId AS eventId,
                   n.topicName AS topicName,
                   n.messagePreview AS messagePreview,
                   n.priority AS priority,
                   n.createdAt AS createdAt
            FROM NotificationEvent n
            WHERE n.role = :role
              AND (n.createdAt < :cursorCreatedAt
                   OR (n.createdAt = :cursorCreatedAt AND n.eventId < :cursorEventId))
            ORDER BY n.createdAt DESC, n.eventId DESC
            """)
    List<NotificationListJpaProjection> findPageAfter(
            @Param("role") UserRole role,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorEventId") UUID cursorEventId,
            Pageable pageable);

    Optional<NotificationEvent> findByEventIdAndRole(UUID eventId, UserRole role);

    @Query("""
            SELECT COUNT(n)
            FROM NotificationEvent n
            WHERE n.role = :role
              AND NOT EXISTS (
                  SELECT r.id
                  FROM NotificationRead r
                  WHERE r.notificationId = n.id AND r.userId = :userId
              )
            """)
    long countUnread(
            @Param("userId") UUID userId,
            @Param("role") UserRole role);
}

@Repository
public class JpaNotificationCentreRepository implements NotificationCentreRepository {

    private final SpringDataNotificationCentreRepository notifications;
    private final SpringDataNotificationReadRepository reads;

    public JpaNotificationCentreRepository(
            SpringDataNotificationCentreRepository notifications,
            SpringDataNotificationReadRepository reads) {
        this.notifications = notifications;
        this.reads = reads;
    }

    @Override
    public List<ProjectedNotification> findPage(
            UUID userId,
            UserRole role,
            NotificationCursor cursor,
            int limit,
            Set<NotificationQueryField> fields) {
        Pageable page = Pageable.ofSize(limit);
        List<NotificationListJpaProjection> summaries = cursor == null
                ? notifications.findFirstPage(role, page)
                : notifications.findPageAfter(
                        role, cursor.createdAt(), cursor.eventId(), page);

        Set<UUID> notificationIds = summaries.stream()
                .map(NotificationListJpaProjection::getNotificationId)
                .collect(Collectors.toUnmodifiableSet());
        Set<UUID> readNotificationIds = notificationIds.isEmpty()
                ? Set.of()
                : reads.findByUserIdAndNotificationIdIn(userId, notificationIds).stream()
                        .map(NotificationRead::getNotificationId)
                        .collect(Collectors.toUnmodifiableSet());

        return summaries.stream()
                .map(summary -> project(
                        summary,
                        readNotificationIds.contains(summary.getNotificationId()),
                        fields))
                .toList();
    }

    @Override
    public Optional<NotificationSharedDetail> findDetail(UUID eventId, UserRole role) {
        return notifications.findByEventIdAndRole(eventId, role)
                .map(notification -> new NotificationSharedDetail(
                        notification.getEventId(),
                        notification.getTopicName(),
                        notification.getMessage(),
                        notification.getPriority(),
                        notification.getCreatedAt()));
    }

    @Override
    public Optional<Instant> findReadAt(UUID eventId, UUID userId, UserRole role) {
        return notifications.findByEventIdAndRole(eventId, role)
                .flatMap(notification -> reads
                        .findByUserIdAndNotificationIdIn(
                                userId, List.of(notification.getId()))
                        .stream()
                        .findFirst()
                        .map(NotificationRead::getReadAt));
    }

    @Override
    public long countUnread(UUID userId, UserRole role) {
        return notifications.countUnread(userId, role);
    }

    private ProjectedNotification project(
            NotificationListJpaProjection summary,
            boolean read,
            Set<NotificationQueryField> fields) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (NotificationQueryField field : fields) {
            values.put(field.apiName(), projectedValue(summary, read, field));
        }
        return new ProjectedNotification(
                Collections.unmodifiableMap(new LinkedHashMap<>(values)),
                summary.getCreatedAt(),
                summary.getEventId());
    }

    private Object projectedValue(
            NotificationListJpaProjection summary,
            boolean read,
            NotificationQueryField field) {
        return switch (field) {
            case EVENT_ID -> summary.getEventId();
            case TOPIC_NAME -> summary.getTopicName();
            case MESSAGE_PREVIEW -> summary.getMessagePreview();
            case PRIORITY -> summary.getPriority();
            case CREATED_AT -> summary.getCreatedAt();
            case IS_READ -> read;
        };
    }
}
