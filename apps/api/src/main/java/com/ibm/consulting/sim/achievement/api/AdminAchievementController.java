package com.ibm.consulting.sim.achievement.api;

import com.ibm.consulting.sim.achievement.application.AchievementAdminView;
import com.ibm.consulting.sim.achievement.application.AdminAchievementService;
import com.ibm.consulting.sim.achievement.application.UpsertAchievementRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Administrative achievement authoring API: full CRUD over gamification rules.
 * Restricted to {@code ADMINISTRATOR} — achievements are a platform-wide progression
 * concept (unlike scenario content, which {@code SCENARIO_AUTHOR} may also manage).
 */
@RestController
@RequestMapping("/api/v1/admin/achievements")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class AdminAchievementController {

    private final AdminAchievementService adminAchievementService;

    public AdminAchievementController(AdminAchievementService adminAchievementService) {
        this.adminAchievementService = adminAchievementService;
    }

    @GetMapping
    List<AchievementAdminView> listAll() {
        return adminAchievementService.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AchievementAdminView create(@Valid @RequestBody UpsertAchievementRequest request) {
        return adminAchievementService.create(request);
    }

    @PutMapping("/{id}")
    AchievementAdminView update(@PathVariable UUID id, @Valid @RequestBody UpsertAchievementRequest request) {
        return adminAchievementService.update(id, request);
    }

    @PatchMapping("/{id}/activate")
    AchievementAdminView activate(@PathVariable UUID id) {
        return adminAchievementService.setActive(id, true);
    }

    @PatchMapping("/{id}/deactivate")
    AchievementAdminView deactivate(@PathVariable UUID id) {
        return adminAchievementService.setActive(id, false);
    }
}
