package com.ibm.consulting.sim.outreach.domain;

import java.util.Optional;
import java.util.UUID;

public interface CapabilityBriefRepository {
    CapabilityBrief save(CapabilityBrief brief);
    Optional<CapabilityBrief> findByEngagementId(UUID engagementId);
}
