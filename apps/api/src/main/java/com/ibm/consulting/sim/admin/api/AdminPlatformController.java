package com.ibm.consulting.sim.admin.api;

import com.ibm.consulting.sim.admin.application.AdminNotificationService;
import com.ibm.consulting.sim.admin.application.NotificationQueryService;
import com.ibm.consulting.sim.admin.application.NotificationReadStatus;
import com.ibm.consulting.sim.admin.application.NotificationReadStatusPage;
import com.ibm.consulting.sim.admin.application.PlatformOverviewResponse;
import com.ibm.consulting.sim.admin.application.PlatformOverviewService;
import com.ibm.consulting.sim.admin.domain.NotificationPriority;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/platform")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class AdminPlatformController {
    private final PlatformOverviewService overviewService;
    private final AdminNotificationService adminNotificationService;
    private final NotificationQueryService notificationQueryService;

    public AdminPlatformController(PlatformOverviewService overviewService,
                                   AdminNotificationService adminNotificationService,
                                   NotificationQueryService notificationQueryService) {
        this.overviewService = overviewService;
        this.adminNotificationService = adminNotificationService;
        this.notificationQueryService = notificationQueryService;
    }

    record PublishNotificationRequest(
            @NotBlank @Size(max = 160) String topicName,
            @NotBlank @Size(max = 4000) String message,
            @NotEmpty List<@NotNull UserRole> roles,
            NotificationPriority priority) {

        NotificationPriority effectivePriority() {
            return NotificationPriority.normalize(priority);
        }
    }

    record PublishNotificationResponse(String status, int publishedCount, List<UserRole> roles) {
    }

    @GetMapping("/overview")
    PlatformOverviewResponse overview() {
        return overviewService.getOverview();
    }

    @PostMapping("/publish-notifications")
    @ResponseStatus(HttpStatus.ACCEPTED)
    PublishNotificationResponse publishNotification(
            @Valid @RequestBody PublishNotificationRequest request,
            @AuthenticationPrincipal User user) {
        var result = adminNotificationService.notifyRoles(
                user.getId(),
                request.topicName(), request.message(), request.roles(),
                request.effectivePriority());
        return new PublishNotificationResponse(
                "ACCEPTED", result.publishedCount(), result.roles());
    }

    @GetMapping("/notifications/{eventId}/read-status")
    NotificationReadStatus notificationReadStatus(@PathVariable UUID eventId) {
        return notificationQueryService.getReadStatus(eventId);
    }

    @GetMapping("/notifications/{eventId}/read-status/users")
    NotificationReadStatusPage notificationReadStatusUsers(
            @PathVariable UUID eventId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor) {
        return notificationQueryService.getReadStatusUsers(eventId, limit, cursor);
    }
}
