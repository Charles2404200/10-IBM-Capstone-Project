package com.ibm.consulting.sim.assessment.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentRepository {
    Assessment save(Assessment assessment);
    Optional<Assessment> findByEngagementId(UUID engagementId);
    List<Assessment> findAllByEngagementIdIn(List<UUID> engagementIds);
}
