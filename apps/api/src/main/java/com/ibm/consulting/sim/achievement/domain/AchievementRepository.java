package com.ibm.consulting.sim.achievement.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AchievementRepository {
    Achievement save(Achievement achievement);
    Optional<Achievement> findById(UUID id);
    List<Achievement> findAll();
    List<Achievement> findAllActive();
}
