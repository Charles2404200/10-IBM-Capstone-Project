package com.ibm.consulting.sim.engagement.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EngagementRepository {
    Engagement save(Engagement engagement);
    Optional<Engagement> findById(UUID id);
    List<Engagement> findByUserId(UUID userId);
    Optional<Engagement> findByIdAndUserId(UUID id, UUID userId);
}
