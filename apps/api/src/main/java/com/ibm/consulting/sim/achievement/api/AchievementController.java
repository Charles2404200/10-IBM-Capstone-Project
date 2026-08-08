package com.ibm.consulting.sim.achievement.api;

import com.ibm.consulting.sim.achievement.application.AchievementQueryService;
import com.ibm.consulting.sim.achievement.application.AchievementSummary;
import com.ibm.consulting.sim.identity.domain.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Learner-facing achievements read API: progression, badges, unlock status. */
@RestController
@RequestMapping("/api/v1/achievements")
public class AchievementController {

    private final AchievementQueryService achievementQueryService;

    public AchievementController(AchievementQueryService achievementQueryService) {
        this.achievementQueryService = achievementQueryService;
    }

    @GetMapping("/me")
    List<AchievementSummary> myAchievements(@AuthenticationPrincipal User user) {
        return achievementQueryService.listForUser(user.getId());
    }
}
