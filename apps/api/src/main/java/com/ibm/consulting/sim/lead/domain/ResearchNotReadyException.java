package com.ibm.consulting.sim.lead.domain;

import com.ibm.consulting.sim.shared.domain.DomainException;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;

import java.util.List;

/** Thrown when a learner tries to advance past Client Intelligence before satisfying {@link ResearchReadinessPolicy}. */
public class ResearchNotReadyException extends DomainException {
    public ResearchNotReadyException(long evidenceCount, boolean hasStakeholder, boolean hasHypothesis, int confidencePercent) {
        super("Research is not yet complete (evidence=%d/%d, stakeholderEvidence=%s, hypothesis=%s, confidence=%d%%/%d%%)"
                .formatted(evidenceCount, ResearchReadinessPolicy.MIN_EVIDENCE_COUNT, hasStakeholder, hasHypothesis,
                        confidencePercent, ResearchReadinessPolicy.MIN_CONFIDENCE_PERCENT));
    }

    public ResearchNotReadyException(List<ResearchEvidence> evidence, DifficultyProfile profile) {
        super(message(evidence, profile));
    }

    private static String message(List<ResearchEvidence> evidence, DifficultyProfile profile) {
        ResearchReadinessPolicy.QualityAssessment quality = ResearchReadinessPolicy.assess(evidence);
        int requiredEvidence = profile == null
                ? ResearchReadinessPolicy.MIN_EVIDENCE_COUNT : profile.requiredEvidenceCount();
        int requiredConfidence = profile == null
                ? ResearchReadinessPolicy.MIN_CONFIDENCE_PERCENT : profile.requiredConfidencePercent();
        return "Research is not yet complete (evidence=%d/%d, coverage=%d/%d, stakeholderEvidence=%s, groundedHypothesis=%s, confidence=%d%%/%d%%)"
                .formatted(ResearchReadinessPolicy.evidenceCount(evidence), requiredEvidence,
                        quality.coverageCount(), ResearchReadinessPolicy.requiredCoverageCount(profile),
                        ResearchReadinessPolicy.hasStakeholderEvidence(evidence), quality.groundedHypothesis(),
                        quality.confidencePercent(), requiredConfidence);
    }
}
