package com.ibm.consulting.sim.lead.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ResearchEvidenceRepository {
    ResearchEvidence save(ResearchEvidence evidence);
    List<ResearchEvidence> findByEngagementId(UUID engagementId);
    long countByEngagementId(UUID engagementId);
    List<ResearchEvidence> findByIdInAndEngagementId(List<UUID> ids, UUID engagementId);
    Map<UUID, Long> countByEngagementIds(List<UUID> engagementIds);
}
