package com.ibm.consulting.sim.outreach.application;

import com.ibm.consulting.sim.outreach.domain.CapabilityBrief;

import java.time.Instant;
import java.util.UUID;

public record CapabilityBriefResponse(
        UUID id,
        UUID engagementId,
        String relevantExperience,
        String approach,
        String caseExample,
        String clientFit,
        String clientReply,
        String outcome,
        Integer scoreClientFit,
        Integer scoreIndustryRelevance,
        Integer scoreEvidenceQuality,
        Integer scoreClarity,
        Integer scoreCredibility,
        Instant updatedAt) {

    public static CapabilityBriefResponse from(CapabilityBrief brief) {
        return new CapabilityBriefResponse(
                brief.getId(), brief.getEngagementId(), brief.getRelevantExperience(), brief.getApproach(),
                brief.getCaseExample(), brief.getClientFit(), brief.getClientReply(), brief.getOutcome().name(),
                brief.getScoreClientFit(), brief.getScoreIndustryRelevance(), brief.getScoreEvidenceQuality(),
                brief.getScoreClarity(), brief.getScoreCredibility(), brief.getUpdatedAt());
    }
}
