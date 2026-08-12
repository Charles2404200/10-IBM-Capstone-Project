package com.ibm.consulting.sim.engagement.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EngagementRepository {
    Engagement save(Engagement engagement);
    List<Engagement> findAll();
    Optional<Engagement> findById(UUID id);
    List<Engagement> findByUserId(UUID userId);
    /** Dashboard projection with the small lifecycle event collection loaded in one query. */
    List<Engagement> findDashboardByUserId(UUID userId);
    Optional<Engagement> findByIdAndUserId(UUID id, UUID userId);
}
