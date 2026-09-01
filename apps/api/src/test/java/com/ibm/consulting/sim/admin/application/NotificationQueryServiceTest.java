package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.admin.domain.NotificationEvent;
import com.ibm.consulting.sim.admin.domain.NotificationRead;
import com.ibm.consulting.sim.admin.domain.NotificationReadRepository;
import com.ibm.consulting.sim.admin.domain.NotificationRepository;
import com.ibm.consulting.sim.admin.domain.NotificationPriority;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationReadRepository notificationReadRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationQueryService service;

    @Test
    void inboxIncludesTheNotificationTopicName() {
        UUID userId = UUID.randomUUID();
        NotificationEvent notification = NotificationEvent.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Maintenance Notice",
                "The platform will restart tonight.",
                UserRole.LEARNER);
        when(notificationRepository.findNotificationsByRole(UserRole.LEARNER, 50))
                .thenReturn(List.of(notification));
        when(notificationReadRepository.findReadNotificationsByUserId(
                userId,
                List.of(notification.getId())))
                .thenReturn(List.of());

        List<NotificationResponse> response = service.listForUser(userId, UserRole.LEARNER);

        assertEquals(1, response.size());
        assertEquals("Maintenance Notice", response.getFirst().topicName());
        assertFalse(response.getFirst().read());
        assertNull(response.getFirst().readAt());
    }

    @Test
    void notificationResponseIncludesCriticalPriority() {
        UUID userId = UUID.randomUUID();
        NotificationEvent notification = NotificationEvent.create(
                UUID.randomUUID(), UUID.randomUUID(), "Security", "Reset now",
                UserRole.LEARNER, NotificationPriority.CRITICAL);
        when(notificationRepository.findNotificationsByRole(UserRole.LEARNER, 50))
                .thenReturn(List.of(notification));
        when(notificationReadRepository.findReadNotificationsByUserId(
                userId, List.of(notification.getId())))
                .thenReturn(List.of());

        List<NotificationResponse> response = service.listForUser(userId, UserRole.LEARNER);

        assertEquals(NotificationPriority.CRITICAL, response.getFirst().priority());
    }

    @Test
    void duplicateReadAcknowledgementIsIdempotent() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        NotificationEvent notification = NotificationEvent.create(
                eventId, UUID.randomUUID(), "Update", "Message", UserRole.LEARNER);
        when(notificationRepository.findByEventIdAndRole(eventId, UserRole.LEARNER))
                .thenReturn(Optional.of(notification));
        when(notificationReadRepository.createReadNotificationForUser(
                notification.getId(), userId, UserRole.LEARNER))
                .thenReturn(false);

        service.markRead(eventId, userId, UserRole.LEARNER);
    }

    @Test
    void roleMismatchIsReportedAsNotFoundWhenMarkingRead() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(notificationRepository.findByEventIdAndRole(eventId, UserRole.LEARNER))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.markRead(eventId, userId, UserRole.LEARNER));
    }

    @Test
    void readStatusIncludesOnlyCurrentActiveUsersInTargetRole() {
        UUID eventId = UUID.randomUUID();
        User readLearner = User.create("read@example.com", "hash", "Read Learner", UserRole.LEARNER);
        User unreadLearner = User.create("unread@example.com", "hash", "Unread Learner", UserRole.LEARNER);
        Instant readAt = Instant.parse("2026-08-20T01:15:00Z");

        NotificationEvent notification = NotificationEvent.create(
                eventId, UUID.randomUUID(), "Scenario Update", "Message", UserRole.LEARNER);
        NotificationRead readReceipt = NotificationRead.create(
                notification.getId(), readLearner.getId(), readAt);

        when(notificationRepository.findByEventId(eventId))
                .thenReturn(Optional.of(notification));
        when(notificationReadRepository.findByNotificationId(notification.getId()))
                .thenReturn(List.of(readReceipt));
        when(userRepository.findAllActiveByRole(UserRole.LEARNER))
                .thenReturn(List.of(readLearner, unreadLearner));

        NotificationReadStatus status = service.getReadStatus(eventId);

        assertEquals(2, status.recipientCount());
        assertEquals(1, status.readCount());
        assertEquals(1, status.unreadCount());
        assertEquals(2, status.users().size());
        NotificationReadStatus.UserReadStatus readStatus = status.users().stream()
                .filter(user -> user.userId().equals(readLearner.getId()))
                .findFirst()
                .orElseThrow();
        NotificationReadStatus.UserReadStatus unreadStatus = status.users().stream()
                .filter(user -> user.userId().equals(unreadLearner.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(readStatus.read());
        assertEquals(readAt, readStatus.readAt());
        assertFalse(unreadStatus.read());
    }
}
