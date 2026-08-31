package com.ibm.consulting.sim.admin.api;

import com.ibm.consulting.sim.admin.application.NotificationQueryService;
import com.ibm.consulting.sim.admin.application.NotificationResponse;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    public NotificationController(NotificationQueryService notificationQueryService) {
        this.notificationQueryService = notificationQueryService;
    }

    @GetMapping
    List<NotificationResponse> list(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "50") int limit) {
        return notificationQueryService.listForUser(user.getId(), user.getRole(), limit);
    }

    @PatchMapping("/{eventId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@PathVariable UUID eventId, @AuthenticationPrincipal User user) {
        notificationQueryService.markRead(eventId, user.getId(), user.getRole());
    }
}
