package com.ibm.consulting.sim.achievement.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Records that a specific user has unlocked a specific achievement, and when. */
@Entity
@Table(name = "user_achievements")
public class UserAchievement extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "achievement_id", nullable = false)
    private UUID achievementId;

    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt;

    protected UserAchievement() {}

    public static UserAchievement unlock(UUID userId, UUID achievementId) {
        UserAchievement ua = new UserAchievement();
        ua.userId = userId;
        ua.achievementId = achievementId;
        ua.unlockedAt = Instant.now();
        return ua;
    }

    public UUID getUserId() { return userId; }
    public UUID getAchievementId() { return achievementId; }
    public Instant getUnlockedAt() { return unlockedAt; }
}
