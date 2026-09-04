package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.admin.domain.*;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotificationQueryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationQueryService.class);
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final NotificationReadRepository notificationReadRepository;
    private final NotificationCentreRepository notificationCentreRepository;
    private final NotificationDetailCacheService detailCacheService;
    private final NotificationCursorCodec cursorCodec;
    private final NotificationMetrics metrics;
    private final NotificationReadStatusCursorCodec readStatusCursorCodec;

    public NotificationQueryService(NotificationRepository notificationRepository,
                                    NotificationReadRepository notificationReadRepository,
                                    NotificationCentreRepository notificationCentreRepository,
                                    NotificationDetailCacheService detailCacheService,
                                    NotificationCursorCodec cursorCodec,
                                    NotificationMetrics metrics,
                                    NotificationReadStatusCursorCodec readStatusCursorCodec) {
        this.notificationRepository = notificationRepository;
        this.notificationReadRepository = notificationReadRepository;
        this.notificationCentreRepository = notificationCentreRepository;
        this.detailCacheService = detailCacheService;
        this.cursorCodec = cursorCodec;
        this.metrics = metrics;
        this.readStatusCursorCodec = readStatusCursorCodec;
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse pageForUser(
            UUID userId,
            UserRole role,
            int limit,
            String encodedCursor,
            String requestedFields) {
        validatePageSize(limit);
        NotificationCursor cursor = cursorCodec.decode(encodedCursor);
        Set<NotificationQueryField> fields = NotificationQueryField.parse(requestedFields);

        List<ProjectedNotification> rows = notificationCentreRepository.findPage(
                userId, role, cursor, limit + 1, fields);
        boolean hasMore = rows.size() > limit;
        List<ProjectedNotification> pageRows = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = null;
        if (hasMore && !pageRows.isEmpty()) {
            ProjectedNotification last = pageRows.getLast();
            nextCursor = cursorCodec.encode(new NotificationCursor(
                    last.cursorCreatedAt(), last.cursorEventId()));
        }
        return new NotificationPageResponse(
                pageRows.stream().map(ProjectedNotification::fields).toList(),
                nextCursor,
                hasMore);
    }

    @Transactional(readOnly = true)
    public NotificationDetailResponse detailForUser(UUID eventId, UUID userId, UserRole role) {
        NotificationSharedDetail detail = detailCacheService.get(eventId, role);
        Optional<java.time.Instant> readAt = notificationCentreRepository.findReadAt(
                eventId, userId, role);
        return new NotificationDetailResponse(
                detail.eventId(), detail.topicName(), detail.message(), detail.priority(),
                detail.createdAt(), readAt.isPresent(), readAt.orElse(null));
    }

    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse unreadCount(UUID userId, UserRole role) {
        return new UnreadNotificationCountResponse(
                notificationCentreRepository.countUnread(userId, role));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listForUser(UUID userId, UserRole role) {
        return listForUser(userId, role, DEFAULT_PAGE_SIZE);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listForUser(UUID userId, UserRole role, int limit) {
        validatePageSize(limit);
        log.debug("Fetching notifications for user: userId={}, role={}", userId, role);

        List<NotificationEvent> notificationsRoleOnly =
                notificationRepository.findNotificationsByRole(role, limit);

        log.debug("Found {} notifications for role={}",
                notificationsRoleOnly.size(), role);

        Map<UUID, NotificationRead> reads = notificationReadRepository
                .findReadNotificationsByUserId(
                        userId,
                        notificationsRoleOnly.stream()
                                .map(NotificationEvent::getId)
                                .toList()
                )
                .stream()
                .collect(Collectors.toMap(
                        NotificationRead::getNotificationId,
                        Function.identity()
                ));

        log.debug("Found {} read notifications for userId={}",
                reads.size(), userId);

        List<NotificationResponse> userNotifications = notificationsRoleOnly.stream()
                .map(notification -> {
                    NotificationRead read = reads.get(notification.getId());

                    return new NotificationResponse(
                            notification.getEventId(),
                            notification.getUserId(),
                            notification.getTopicName(),
                            notification.getMessage(),
                            notification.getRole(),
                            notification.getPriority(),
                            notification.getCreatedAt(),
                            read != null,
                            read == null ? null : read.getReadAt()
                    );
                })
                .toList();

        log.info(
                "Fetched notifications for user: userId={}, role={}, totalCount={}, readCount={}, unreadCount={}",
                userId,
                role,
                userNotifications.size(),
                reads.size(),
                userNotifications.size() - reads.size()
        );

        return userNotifications;
    }

    private void validatePageSize(int limit) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new InvalidNotificationQueryException(
                    "limit must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    @Transactional
    public void markRead(UUID eventId, UUID userId, UserRole role) {
        log.debug(
                "Marking notification as read: eventId={}, userId={}, role={}",
                eventId,
                userId,
                role
        );

        NotificationEvent event = notificationRepository
                .findByEventIdAndRole(eventId, role)
                .orElseThrow(() -> {
                    log.warn(
                            "Notification not found or inaccessible: eventId={}, userId={}, role={}",
                            eventId,
                            userId,
                            role
                    );

                    return new NotFoundException("Notification", eventId);
                });

        boolean added = notificationReadRepository
                .createReadNotificationForUser(
                        event.getId(),
                        userId,
                        role
                );
        recordReadMetricAfterCommit(added);

        if (!added) {
            // PATCH is idempotent: a concurrent request may have inserted the
            // same unique receipt first, which is already the desired state.
            log.debug(
                    "Notification was already marked read: eventId={}, userId={}, role={}",
                    eventId,
                    userId,
                    role
            );
        }

        log.info(
                "Notification marked as read: eventId={}, userId={}, role={}",
                eventId,
                userId,
                role
        );
    }

    @Transactional(readOnly = true)
    public List<NotificationReadReceipt> findReadReceipts(UUID eventId) {
        return notificationRepository.findByEventId(eventId)
                .map(event -> notificationReadRepository.findByNotificationId(event.getId()).stream()
                        .map(read -> new NotificationReadReceipt(read.getUserId(), read.getReadAt()))
                        .toList())
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public NotificationReadStatus getReadStatus(UUID eventId) {
        log.debug("Fetching notification read status: eventId={}", eventId);

        NotificationEvent notification = notificationRepository
                .findByEventId(eventId)
                .orElseThrow(() -> {
                    log.warn(
                            "Notification not found while fetching read status: eventId={}",
                            eventId
                    );

                    return new NotFoundException("Notification", eventId);
                });

        log.debug(
                "Notification found for read status: eventId={}, role={}",
                eventId,
                notification.getRole()
        );

        NotificationReadStatusCounts counts = notificationCentreRepository
                .countAudienceReadStatus(notification.getId(), notification.getRole());

        log.info(
                "Notification read status fetched: eventId={}, role={}, totalUsers={}, readCount={}, unreadCount={}",
                eventId,
                notification.getRole(),
                counts.recipientCount(),
                counts.readCount(),
                counts.unreadCount()
        );

        return new NotificationReadStatus(
                eventId,
                notification.getRole(),
                counts.recipientCount(),
                counts.readCount(),
                counts.unreadCount()
        );
    }

    private void recordReadMetricAfterCommit(boolean created) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Direct unit-test invocation has no proxy-created transaction.
            metrics.recordReadMarked(created);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                metrics.recordReadMarked(created);
            }
        });
    }

    @Transactional(readOnly = true)
    public NotificationReadStatusPage getReadStatusUsers(
            UUID eventId, int limit, String encodedCursor) {
        validatePageSize(limit);
        NotificationEvent notification = notificationRepository.findByEventId(eventId)
                .orElseThrow(() -> new NotFoundException("Notification", eventId));
        NotificationReadStatusCursor cursor = readStatusCursorCodec.decode(encodedCursor);
        List<NotificationUserReadStatus> rows = notificationCentreRepository
                .findAudienceReadStatusPage(
                        notification.getId(), notification.getRole(), cursor, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<NotificationUserReadStatus> pageRows = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = null;
        if (hasMore && !pageRows.isEmpty()) {
            NotificationUserReadStatus last = pageRows.getLast();
            nextCursor = readStatusCursorCodec.encode(new NotificationReadStatusCursor(
                    last.cursorDisplayName(), last.userId()));
        }
        return new NotificationReadStatusPage(
                pageRows.stream()
                        .map(row -> new NotificationReadStatusPage.UserReadStatus(
                                row.userId(), row.displayName(), row.read(), row.readAt()))
                        .toList(),
                nextCursor,
                hasMore);
    }
}
