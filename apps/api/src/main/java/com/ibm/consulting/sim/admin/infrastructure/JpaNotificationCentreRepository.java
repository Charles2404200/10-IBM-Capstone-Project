package com.ibm.consulting.sim.admin.infrastructure;

import com.ibm.consulting.sim.admin.application.NotificationCursor;
import com.ibm.consulting.sim.admin.application.NotificationQueryField;
import com.ibm.consulting.sim.admin.application.NotificationSharedDetail;
import com.ibm.consulting.sim.admin.application.NotificationReadStatusCounts;
import com.ibm.consulting.sim.admin.application.NotificationReadStatusCursor;
import com.ibm.consulting.sim.admin.application.NotificationUserReadStatus;
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

interface NotificationReadStatusCountsJpaProjection {
    long getRecipientCount();
    long getReadCount();
}

interface NotificationUserReadStatusJpaProjection {
    UUID getUserId();
    String getDisplayName();
    Boolean getReadFlag();
    Instant getReadAt();
    String getCursorDisplayName();
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

    @Query("""
            SELECT COUNT(recipient.id) AS recipientCount,
                   COUNT(receipt.id) AS readCount
              FROM User recipient
              LEFT JOIN NotificationRead receipt
                ON receipt.userId = recipient.id
               AND receipt.notificationId = :notificationId
             WHERE recipient.active = true
               AND recipient.role = :role
            """)
    NotificationReadStatusCountsJpaProjection countAudienceReadStatus(
            @Param("notificationId") UUID notificationId,
            @Param("role") UserRole role);

    @Query("""
            SELECT recipient.id AS userId,
                   recipient.displayName AS displayName,
                   CASE WHEN receipt.id IS NULL THEN false ELSE true END AS readFlag,
                   receipt.readAt AS readAt,
                   LOWER(recipient.displayName) AS cursorDisplayName
              FROM User recipient
              LEFT JOIN NotificationRead receipt
                ON receipt.userId = recipient.id
               AND receipt.notificationId = :notificationId
             WHERE recipient.active = true
               AND recipient.role = :role
             ORDER BY LOWER(recipient.displayName) ASC, recipient.id ASC
            """)
    List<NotificationUserReadStatusJpaProjection> findFirstAudienceReadStatusPage(
            @Param("notificationId") UUID notificationId,
            @Param("role") UserRole role,
            Pageable pageable);

    @Query("""
            SELECT recipient.id AS userId,
                   recipient.displayName AS displayName,
                   CASE WHEN receipt.id IS NULL THEN false ELSE true END AS readFlag,
                   receipt.readAt AS readAt,
                   LOWER(recipient.displayName) AS cursorDisplayName
              FROM User recipient
              LEFT JOIN NotificationRead receipt
                ON receipt.userId = recipient.id
               AND receipt.notificationId = :notificationId
             WHERE recipient.active = true
               AND recipient.role = :role
               AND (LOWER(recipient.displayName) > :cursorDisplayName
                    OR (LOWER(recipient.displayName) = :cursorDisplayName
                        AND recipient.id > :cursorUserId))
             ORDER BY LOWER(recipient.displayName) ASC, recipient.id ASC
            """)
    List<NotificationUserReadStatusJpaProjection> findAudienceReadStatusPageAfter(
            @Param("notificationId") UUID notificationId,
            @Param("role") UserRole role,
            @Param("cursorDisplayName") String cursorDisplayName,
            @Param("cursorUserId") UUID cursorUserId,
            Pageable pageable);
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

    @Override
    public NotificationReadStatusCounts countAudienceReadStatus(
            UUID notificationId, UserRole role) {
        NotificationReadStatusCountsJpaProjection counts =
                notifications.countAudienceReadStatus(notificationId, role);
        return new NotificationReadStatusCounts(
                counts.getRecipientCount(), counts.getReadCount());
    }

    @Override
    public List<NotificationUserReadStatus> findAudienceReadStatusPage(
            UUID notificationId,
            UserRole role,
            NotificationReadStatusCursor cursor,
            int limit) {
        Pageable page = Pageable.ofSize(limit);
        List<NotificationUserReadStatusJpaProjection> rows = cursor == null
                ? notifications.findFirstAudienceReadStatusPage(notificationId, role, page)
                : notifications.findAudienceReadStatusPageAfter(
                        notificationId, role, cursor.displayName(), cursor.userId(), page);
        return rows.stream()
                .map(row -> new NotificationUserReadStatus(
                        row.getUserId(), row.getDisplayName(),
                        // this Boolean.TRUE.equals is used so if it is
                        // null then make it as false instead of putting
                        // null which might give error
                        Boolean.TRUE.equals(row.getReadFlag()), row.getReadAt(),
                        row.getCursorDisplayName()))
                .toList();
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
