package com.ibm.consulting.sim.admin.infrastructure;

import com.ibm.consulting.sim.admin.application.NotificationQueryField;
import com.ibm.consulting.sim.admin.application.NotificationReadStatusCursor;
import com.ibm.consulting.sim.admin.domain.NotificationPriority;
import com.ibm.consulting.sim.admin.domain.NotificationRead;
import com.ibm.consulting.sim.identity.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class JpaNotificationCentreRepositoryTest {

    @Mock
    private SpringDataNotificationCentreRepository notifications;
    @Mock
    private SpringDataNotificationReadRepository reads;
    @Mock
    private NotificationListJpaProjection summary;

    private JpaNotificationCentreRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JpaNotificationCentreRepository(notifications, reads);
    }

    @Test
    void pageUsesClosedProjectionAndOneBatchedReadQuery() {
        UUID notificationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-02T00:00:00Z");
        when(summary.getNotificationId()).thenReturn(notificationId);
        when(summary.getEventId()).thenReturn(eventId);
        when(summary.getTopicName()).thenReturn("Course published");
        when(summary.getCreatedAt()).thenReturn(createdAt);
        when(notifications.findFirstPage(eq(UserRole.LEARNER), any(Pageable.class)))
                .thenReturn(List.of(summary));
        when(reads.findByUserIdAndNotificationIdIn(userId, Set.of(notificationId)))
                .thenReturn(List.of(NotificationRead.create(
                        notificationId, userId, createdAt)));

        Set<NotificationQueryField> requestedFields = new LinkedHashSet<>(List.of(
                NotificationQueryField.TOPIC_NAME,
                NotificationQueryField.EVENT_ID,
                NotificationQueryField.IS_READ));
        var page = repository.findPage(
                userId,
                UserRole.LEARNER,
                null,
                31,
                requestedFields);

        assertEquals(1, page.size());
        assertEquals(
                Set.of("eventId", "topicName", "isRead"),
                page.getFirst().fields().keySet());
        assertEquals(
                List.of("topicName", "eventId", "isRead"),
                new ArrayList<>(page.getFirst().fields().keySet()));
        assertEquals(true, page.getFirst().fields().get("isRead"));
        verify(reads).findByUserIdAndNotificationIdIn(userId, Set.of(notificationId));
    }

    @Test
    void emptyPageDoesNotQueryReadReceipts() {
        UUID userId = UUID.randomUUID();
        when(notifications.findFirstPage(eq(UserRole.REVIEWER), any(Pageable.class)))
                .thenReturn(List.of());

        assertEquals(
                List.of(),
                repository.findPage(
                        userId,
                        UserRole.REVIEWER,
                        null,
                        30,
                        NotificationQueryField.defaults()));

        verify(reads, never()).findByUserIdAndNotificationIdIn(any(), any());
    }

    @Test
    void administratorAudienceStatusUsesAggregateAndBoundedCursorProjection() {
        UUID notificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        NotificationReadStatusCountsJpaProjection counts =
                mock(NotificationReadStatusCountsJpaProjection.class);
        NotificationUserReadStatusJpaProjection user =
                mock(NotificationUserReadStatusJpaProjection.class);
        when(counts.getRecipientCount()).thenReturn(10L);
        when(counts.getReadCount()).thenReturn(4L);
        when(notifications.countAudienceReadStatus(notificationId, UserRole.LEARNER))
                .thenReturn(counts);
        when(user.getUserId()).thenReturn(userId);
        when(user.getDisplayName()).thenReturn("Alice");
        when(user.getCursorDisplayName()).thenReturn("alice");
        when(user.getReadFlag()).thenReturn(true);
        when(notifications.findAudienceReadStatusPageAfter(
                eq(notificationId), eq(UserRole.LEARNER), eq("alice"), eq(userId),
                any(Pageable.class)))
                .thenReturn(List.of(user));

        var aggregate = repository.countAudienceReadStatus(notificationId, UserRole.LEARNER);
        var page = repository.findAudienceReadStatusPage(
                notificationId, UserRole.LEARNER,
                new NotificationReadStatusCursor("alice", userId), 51);

        assertEquals(10, aggregate.recipientCount());
        assertEquals(4, aggregate.readCount());
        assertEquals(1, page.size());
        assertEquals(userId, page.getFirst().userId());
        assertEquals(true, page.getFirst().read());
    }
}
