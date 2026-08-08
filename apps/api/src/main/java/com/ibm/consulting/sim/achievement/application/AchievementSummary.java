package com.ibm.consulting.sim.achievement.application;

import com.ibm.consulting.sim.achievement.domain.Achievement;

import java.time.Instant;
import java.util.UUID;

/** Learner-facing view of a single achievement: definition + this learner's unlock/progress state. */
public record AchievementSummary(
        UUID id,
        String name,
        String description,
        String iconKey,
        boolean unlocked,
        Instant unlockedAt,
        double progressPercent) {

    public static AchievementSummary locked(Achievement a, double progressPercent) {
        return new AchievementSummary(a.getId(), a.getName(), a.getDescription(), a.getIconKey(),
                false, null, progressPercent);
    }

    public static AchievementSummary unlocked(Achievement a, Instant unlockedAt) {
        return new AchievementSummary(a.getId(), a.getName(), a.getDescription(), a.getIconKey(),
                true, unlockedAt, 100.0);
    }
}
