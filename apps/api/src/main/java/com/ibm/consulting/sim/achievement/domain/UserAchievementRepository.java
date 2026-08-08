package com.ibm.consulting.sim.achievement.domain;

import java.util.List;
import java.util.UUID;

public interface UserAchievementRepository {
    UserAchievement save(UserAchievement userAchievement);
    List<UserAchievement> findByUserId(UUID userId);
    boolean existsByUserIdAndAchievementId(UUID userId, UUID achievementId);
}
