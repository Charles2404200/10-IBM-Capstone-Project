package com.ibm.consulting.sim.achievement.infrastructure;

import com.ibm.consulting.sim.achievement.domain.UserAchievement;
import com.ibm.consulting.sim.achievement.domain.UserAchievementRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
interface SpringDataUserAchievementRepository extends JpaRepository<UserAchievement, UUID> {
    List<UserAchievement> findByUserId(UUID userId);
    boolean existsByUserIdAndAchievementId(UUID userId, UUID achievementId);
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
}
