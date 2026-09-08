package com.ibm.consulting.sim.achievement.infrastructure;

import com.ibm.consulting.sim.achievement.domain.UserAchievement;
import com.ibm.consulting.sim.achievement.domain.UserAchievementRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
interface SpringDataUserAchievementRepository extends JpaRepository<UserAchievement, UUID> {
    List<UserAchievement> findByUserId(UUID userId);
    boolean existsByUserIdAndAchievementId(UUID userId, UUID achievementId);

    @Modifying
    @Query(value = """
            insert into user_achievements
                (id, user_id, achievement_id, unlocked_at, created_at, updated_at, version)
            values
                (:id, :userId, :achievementId, :unlockedAt, :unlockedAt, :unlockedAt, 0)
            on conflict (user_id, achievement_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("userId") UUID userId,
                       @Param("achievementId") UUID achievementId,
                       @Param("unlockedAt") Instant unlockedAt);
}

@Repository
class JpaUserAchievementRepository implements UserAchievementRepository {

    private final SpringDataUserAchievementRepository repo;

    JpaUserAchievementRepository(SpringDataUserAchievementRepository repo) {
        this.repo = repo;
    }

    @Override public UserAchievement save(UserAchievement userAchievement) { return repo.save(userAchievement); }
    @Override public List<UserAchievement> findByUserId(UUID userId) { return repo.findByUserId(userId); }
    @Override public boolean existsByUserIdAndAchievementId(UUID userId, UUID achievementId) {
        return repo.existsByUserIdAndAchievementId(userId, achievementId);
    }
    @Override public boolean insertIfAbsent(UUID userId, UUID achievementId, Instant unlockedAt) {
        return repo.insertIfAbsent(UUID.randomUUID(), userId, achievementId, unlockedAt) == 1;
    }
}
