package com.ibm.consulting.sim.engagement.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Set-based read boundary shared by transition commands and dashboard projections. */
@FunctionalInterface
public interface LifecycleFactRepository {
    Map<UUID, LifecycleFacts> loadAll(List<UUID> engagementIds);
}
