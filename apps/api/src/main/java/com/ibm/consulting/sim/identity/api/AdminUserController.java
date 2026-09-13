package com.ibm.consulting.sim.identity.api;

import com.ibm.consulting.sim.identity.application.AdminUserService;
import com.ibm.consulting.sim.identity.application.AdminUserPage;
import com.ibm.consulting.sim.identity.application.UserSummary;
import com.ibm.consulting.sim.identity.domain.UserDirectoryQuery;
import com.ibm.consulting.sim.identity.domain.UserRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Administrative user management API. Every endpoint is restricted to the
 * {@code ADMINISTRATOR} role — enforced declaratively so authorisation cannot be
 * bypassed by forgetting a check inside a service method.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMINISTRATOR')")
@Validated
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    record ChangeRoleRequest(@NotNull UserRole role) {}

    @GetMapping
    AdminUserPage listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return adminUserService.listUsers(new UserDirectoryQuery(search, role, active, page, size));
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
