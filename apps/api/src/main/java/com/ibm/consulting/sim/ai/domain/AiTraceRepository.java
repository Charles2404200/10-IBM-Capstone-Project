package com.ibm.consulting.sim.ai.domain;

import java.util.List;
import java.util.UUID;

public interface AiTraceRepository {
    AiTrace save(AiTrace trace);
    List<AiTrace> findByEngagementId(UUID engagementId);
}
