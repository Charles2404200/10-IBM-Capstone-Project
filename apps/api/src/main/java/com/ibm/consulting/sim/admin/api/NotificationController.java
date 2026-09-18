package com.ibm.consulting.sim.admin.api;

import com.ibm.consulting.sim.admin.application.NotificationQueryService;
import com.ibm.consulting.sim.admin.application.NotificationDetailResponse;
import com.ibm.consulting.sim.admin.application.NotificationPageResponse;
import com.ibm.consulting.sim.admin.application.UnreadNotificationCountResponse;
import com.ibm.consulting.sim.identity.domain.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    public NotificationController(NotificationQueryService notificationQueryService) {
        this.notificationQueryService = notificationQueryService;
    }

    @GetMapping
    NotificationPageResponse list(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String fields) {
        return notificationQueryService.pageForUser(
                user.getId(), user.getRole(), limit, cursor, fields);
    }

    @GetMapping("/unread-count")
    UnreadNotificationCountResponse unreadCount(@AuthenticationPrincipal User user) {
        return notificationQueryService.unreadCount(user.getId(), user.getRole());
    }

    @GetMapping("/{eventId}")
    NotificationDetailResponse detail(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal User user) {
        return notificationQueryService.detailForUser(
                eventId, user.getId(), user.getRole());
    }

    @PatchMapping("/{eventId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@PathVariable UUID eventId, @AuthenticationPrincipal User user) {
        notificationQueryService.markRead(eventId, user.getId(), user.getRole());
    }
}
