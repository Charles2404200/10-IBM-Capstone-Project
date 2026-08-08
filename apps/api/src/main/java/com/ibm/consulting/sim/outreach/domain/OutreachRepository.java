package com.ibm.consulting.sim.outreach.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutreachRepository {
    OutreachAttempt save(OutreachAttempt attempt);
    List<OutreachAttempt> findByEngagementId(UUID engagementId);
    Optional<OutreachAttempt> findById(UUID id);
    int countByEngagementId(UUID engagementId);
}
