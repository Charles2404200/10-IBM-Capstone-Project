package com.ibm.consulting.sim.identity.api;

import com.ibm.consulting.sim.identity.application.AdminUserService;
import com.ibm.consulting.sim.identity.application.UserSummary;
import com.ibm.consulting.sim.identity.domain.UserRole;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Administrative user management API. Every endpoint is restricted to the
 * {@code ADMINISTRATOR} role — enforced declaratively so authorisation cannot be
 * bypassed by forgetting a check inside a service method.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    record ChangeRoleRequest(@NotNull UserRole role) {}

    @GetMapping
    List<UserSummary> listUsers() {
        return adminUserService.listUsers();
    }

    @PatchMapping("/{userId}/role")
    UserSummary changeRole(@PathVariable UUID userId, @RequestBody ChangeRoleRequest req) {
        return adminUserService.changeRole(userId, req.role());
    }

    @PatchMapping("/{userId}/deactivate")
    UserSummary deactivate(@PathVariable UUID userId) {
        return adminUserService.deactivate(userId);
    }

    @PatchMapping("/{userId}/reactivate")
    UserSummary reactivate(@PathVariable UUID userId) {
        return adminUserService.reactivate(userId);
    }
}
