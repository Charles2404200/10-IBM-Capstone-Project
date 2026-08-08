package com.ibm.consulting.sim.lead.application;

import com.ibm.consulting.sim.lead.domain.ResearchEvidence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record ResearchEvidenceSummary(
        UUID id,
        UUID engagementId,
        String note,
        String hypothesis,
        String evidenceType,
        String sourceUrl,
        String sourceTitle,
        LocalDate occurredOn,
        String confidence,
        int sequenceNo,
        Set<UUID> supportingEvidenceIds,
        Instant createdAt) {

    public static ResearchEvidenceSummary from(ResearchEvidence e) {
        return new ResearchEvidenceSummary(
                e.getId(), e.getEngagementId(), e.getNote(), e.getHypothesis(), e.getEvidenceType().name(),
                e.getSourceUrl(), e.getSourceTitle(), e.getOccurredOn(), e.getConfidence().name(),
                e.getSequenceNo(), e.getSupportingEvidenceIds(), e.getCreatedAt());
    }
}
