package com.ibm.consulting.sim.lead.application;

import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.lead.domain.ResearchEvidence;
import com.ibm.consulting.sim.lead.domain.ResearchReadinessPolicy;

import java.util.List;

/**
 * Read-only "requirements checklist" the Client Intelligence page renders next
 * to the "Proceed to Outreach" action, so the learner can see exactly which
 * conditions are still unmet — mirrors {@code MeetingPreparationResponse}'s
 * readiness surface for the outreach-gating equivalent.
 */
public record ResearchGateStatus(
        boolean researchCompleted,
        long evidenceCount,
        long requiredEvidenceCount,
        boolean hasStakeholderEvidence,
        boolean hasHypothesis,
        int confidencePercent,
        int requiredConfidencePercent,
        boolean ready) {

    public static ResearchGateStatus from(EngagementState state, List<ResearchEvidence> evidence) {
        long count = ResearchReadinessPolicy.evidenceCount(evidence);
        boolean hasStakeholder = ResearchReadinessPolicy.hasStakeholderEvidence(evidence);
        boolean hasHypothesis = ResearchReadinessPolicy.hasHypothesis(evidence);
        int confidence = ResearchReadinessPolicy.confidencePercent(evidence);
        boolean alreadyCompleted = state != EngagementState.QUALIFYING && state != EngagementState.CLIENT_INTELLIGENCE;
        return new ResearchGateStatus(
                alreadyCompleted,
                count,
                ResearchReadinessPolicy.MIN_EVIDENCE_COUNT,
                hasStakeholder,
                hasHypothesis,
                confidence,
                ResearchReadinessPolicy.MIN_CONFIDENCE_PERCENT,
                alreadyCompleted || ResearchReadinessPolicy.isResearchComplete(evidence));
    }
}
