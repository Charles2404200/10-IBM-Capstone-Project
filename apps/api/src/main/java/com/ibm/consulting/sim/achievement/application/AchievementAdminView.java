package com.ibm.consulting.sim.achievement.application;

import com.ibm.consulting.sim.achievement.domain.Achievement;

/** Admin-facing view of an achievement definition, including its full rule tree. */
public record AchievementAdminView(
        java.util.UUID id,
        String name,
        String description,
        String iconKey,
        boolean active,
        ConditionNode rule) {

    public static AchievementAdminView from(Achievement achievement, ConditionNode rule) {
        return new AchievementAdminView(achievement.getId(), achievement.getName(), achievement.getDescription(),
                achievement.getIconKey(), achievement.isActive(), rule);
    }
}
