package com.ibm.consulting.sim.achievement.infrastructure;

import com.ibm.consulting.sim.achievement.domain.Achievement;
import com.ibm.consulting.sim.achievement.domain.AchievementRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataAchievementRepository extends JpaRepository<Achievement, UUID> {
    List<Achievement> findByActiveTrue();
}

@Repository
class JpaAchievementRepository implements AchievementRepository {

    private final SpringDataAchievementRepository repo;

    JpaAchievementRepository(SpringDataAchievementRepository repo) {
        this.repo = repo;
    }

    @Override public Achievement save(Achievement achievement) { return repo.save(achievement); }
    @Override public Optional<Achievement> findById(UUID id) { return repo.findById(id); }
    @Override public List<Achievement> findAll() { return repo.findAll(); }
    @Override public List<Achievement> findAllActive() { return repo.findByActiveTrue(); }
}
