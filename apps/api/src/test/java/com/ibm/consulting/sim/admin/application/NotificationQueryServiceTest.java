package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.admin.domain.NotificationEvent;
import com.ibm.consulting.sim.admin.domain.NotificationCentreRepository;
import com.ibm.consulting.sim.admin.domain.NotificationReadRepository;
import com.ibm.consulting.sim.admin.domain.NotificationRepository;
import com.ibm.consulting.sim.admin.domain.NotificationPriority;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationReadRepository notificationReadRepository;

    @Mock
    private NotificationCentreRepository notificationCentreRepository;

    @Mock
    private NotificationDetailCacheService detailCacheService;

    @Mock
    private NotificationCursorCodec cursorCodec;

    @Mock
    private NotificationMetrics metrics;

    @Mock
    private NotificationReadStatusCursorCodec readStatusCursorCodec;

    @InjectMocks
    private NotificationQueryService service;

    @Test
    void rejectsUnboundedNotificationAndReadStatusPageSizes() {
        assertThrows(InvalidNotificationQueryException.class,
                () -> service.pageForUser(
                        UUID.randomUUID(), UserRole.LEARNER, 101, null, null));
        assertThrows(InvalidNotificationQueryException.class,
                () -> service.getReadStatusUsers(UUID.randomUUID(), 101, null));
    }

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
    void notificationPageUsesLimitPlusOneAndCreatesStableNextCursor() {
        UUID userId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Instant firstAt = Instant.parse("2026-09-02T01:00:00Z");
        Instant secondAt = Instant.parse("2026-09-02T00:59:00Z");
        var fields = NotificationQueryField.defaults();
        var first = new ProjectedNotification(
                java.util.Map.of("eventId", firstId), firstAt, firstId);
        var second = new ProjectedNotification(
                java.util.Map.of("eventId", secondId), secondAt, secondId);

        when(notificationCentreRepository.findPage(
                userId, UserRole.LEARNER, null, 2, fields))
                .thenReturn(List.of(first, second));
        when(cursorCodec.encode(new NotificationCursor(firstAt, firstId)))
                .thenReturn("next-cursor");

        NotificationPageResponse response = service.pageForUser(
                userId, UserRole.LEARNER, 1, null, null);

        assertEquals(1, response.items().size());
        assertTrue(response.hasMore());
        assertEquals("next-cursor", response.nextCursor());
    }

    @Test
    void detailCombinesCachedSharedBodyWithUncachedPerUserReadState() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-02T01:00:00Z");
        Instant readAt = Instant.parse("2026-09-02T01:05:00Z");
        when(detailCacheService.get(eventId, UserRole.LEARNER)).thenReturn(
                new NotificationSharedDetail(
                        eventId, "Course", "Complete body", NotificationPriority.IMPORTANT, createdAt));
        when(notificationCentreRepository.findReadAt(eventId, userId, UserRole.LEARNER))
                .thenReturn(Optional.of(readAt));

        NotificationDetailResponse response = service.detailForUser(
                eventId, userId, UserRole.LEARNER);

        assertEquals("Complete body", response.message());
        assertTrue(response.read());
        assertEquals(readAt, response.readAt());
    }

    @Test
    void unreadCountIsScopedByAuthenticatedIdentityAndRole() {
        UUID userId = UUID.randomUUID();
        when(notificationCentreRepository.countUnread(userId, UserRole.REVIEWER)).thenReturn(17L);

        assertEquals(17, service.unreadCount(userId, UserRole.REVIEWER).unreadCount());
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
    void sequentialReadAcknowledgementsBothReachTheDesiredState() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        NotificationEvent notification = NotificationEvent.create(
                eventId, UUID.randomUUID(), "Update", "Message", UserRole.LEARNER);
        when(notificationRepository.findByEventIdAndRole(eventId, UserRole.LEARNER))
                .thenReturn(Optional.of(notification));
        when(notificationReadRepository.createReadNotificationForUser(
                notification.getId(), userId, UserRole.LEARNER))
                .thenReturn(true, false);

        service.markRead(eventId, userId, UserRole.LEARNER);
        service.markRead(eventId, userId, UserRole.LEARNER);

        verify(notificationReadRepository, times(2)).createReadNotificationForUser(
                notification.getId(), userId, UserRole.LEARNER);
        verify(metrics).recordReadMarked(true);
        verify(metrics).recordReadMarked(false);
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
    void readStatusUsesBoundedDatabaseAggregation() {
        UUID eventId = UUID.randomUUID();
        NotificationEvent notification = NotificationEvent.create(
                eventId, UUID.randomUUID(), "Scenario Update", "Message", UserRole.LEARNER);

        when(notificationRepository.findByEventId(eventId))
                .thenReturn(Optional.of(notification));
        when(notificationCentreRepository.countAudienceReadStatus(
                notification.getId(), UserRole.LEARNER))
                .thenReturn(new NotificationReadStatusCounts(2, 1));

        NotificationReadStatus status = service.getReadStatus(eventId);

        assertEquals(2, status.recipientCount());
        assertEquals(1, status.readCount());
        assertEquals(1, status.unreadCount());
    }

    @Test
    void readStatusUsersUsesBoundedKeysetPagination() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        NotificationEvent notification = NotificationEvent.create(
                eventId, UUID.randomUUID(), "Scenario Update", "Message", UserRole.LEARNER);
        when(notificationRepository.findByEventId(eventId)).thenReturn(Optional.of(notification));
        when(notificationCentreRepository.findAudienceReadStatusPage(
                notification.getId(), UserRole.LEARNER, null, 2))
                .thenReturn(List.of(
                        new NotificationUserReadStatus(
                                userId, "Alice", true, Instant.now(), "alice"),
                        new NotificationUserReadStatus(
                                UUID.randomUUID(), "Bob", false, null, "bob")));
        when(readStatusCursorCodec.encode(any())).thenReturn("next");

        NotificationReadStatusPage page = service.getReadStatusUsers(eventId, 1, null);

        assertEquals(1, page.items().size());
        assertTrue(page.hasMore());
        assertEquals("next", page.nextCursor());
    }
}
