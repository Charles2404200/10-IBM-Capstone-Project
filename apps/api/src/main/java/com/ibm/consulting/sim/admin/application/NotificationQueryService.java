package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.admin.domain.*;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotificationQueryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationQueryService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationReadRepository notificationReadRepository;
    private final UserRepository userRepository;

    public NotificationQueryService(NotificationRepository notificationRepository,
                                    NotificationReadRepository notificationReadRepository,
                                    UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.notificationReadRepository = notificationReadRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listForUser(UUID userId, UserRole role) {
        log.debug("Fetching notifications for user: userId={}, role={}", userId, role);

        List<NotificationEvent> notificationsRoleOnly =
                notificationRepository.findNotificationsByRole(role);

        log.debug("Found {} notifications for role={}",
                notificationsRoleOnly.size(), role);

        Map<UUID, NotificationRead> reads = notificationReadRepository
                .findReadNotificationsByUserId(userId)
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

        if (!added) {
            log.error(
                    "Failed to persist notification read acknowledgement: eventId={}, userId={}, role={}",
                    eventId,
                    userId,
                    role
            );

            throw new NotificationReadException(
                    "Failed to mark notification as read"
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

        Map<UUID, NotificationReadReceipt> receipts = notificationReadRepository
                .findByNotificationId(notification.getId())
                .stream()
                .collect(Collectors.toMap(
                        NotificationRead::getUserId,
                        notificationRead -> new NotificationReadReceipt(
                                notificationRead.getUserId(),
                                notificationRead.getReadAt()
                        )
                ));

        log.debug(
                "Loaded notification read receipts: eventId={}, receiptCount={}",
                eventId,
                receipts.size()
        );

        List<NotificationReadStatus.UserReadStatus> users = userRepository
                .findAllActiveByRole(notification.getRole())
                .stream()
                .sorted(Comparator.comparing(
                        User::getDisplayName,
                        String.CASE_INSENSITIVE_ORDER
                ))
                .map(user -> {
                    NotificationReadReceipt receipt = receipts.get(user.getId());

                    return new NotificationReadStatus.UserReadStatus(
                            user.getId(),
                            user.getDisplayName(),
                            receipt != null,
                            receipt == null ? null : receipt.readAt()
                    );
                })
                .toList();

        int readCount = (int) users.stream()
                .filter(NotificationReadStatus.UserReadStatus::read)
                .count();

        int unreadCount = users.size() - readCount;

        log.info(
                "Notification read status fetched: eventId={}, role={}, totalUsers={}, readCount={}, unreadCount={}",
                eventId,
                notification.getRole(),
                users.size(),
                readCount,
                unreadCount
        );

        return new NotificationReadStatus(
                eventId,
                notification.getRole(),
                users.size(),
                readCount,
                unreadCount,
                users
        );
    }
}
