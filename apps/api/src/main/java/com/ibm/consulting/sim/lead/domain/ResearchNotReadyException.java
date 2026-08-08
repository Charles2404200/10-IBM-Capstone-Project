package com.ibm.consulting.sim.lead.domain;

import com.ibm.consulting.sim.shared.domain.DomainException;

/** Thrown when a learner tries to advance past Client Intelligence before satisfying {@link ResearchReadinessPolicy}. */
public class ResearchNotReadyException extends DomainException {
    public ResearchNotReadyException(long evidenceCount, boolean hasStakeholder, boolean hasHypothesis, int confidencePercent) {
        super("Research is not yet complete (evidence=%d/%d, stakeholderEvidence=%s, hypothesis=%s, confidence=%d%%/%d%%)"
                .formatted(evidenceCount, ResearchReadinessPolicy.MIN_EVIDENCE_COUNT, hasStakeholder, hasHypothesis,
                        confidencePercent, ResearchReadinessPolicy.MIN_CONFIDENCE_PERCENT));
    }
}
