package com.ibm.consulting.sim.assessment.domain;

/**
 * Tracks the non-authoritative coaching enrichment independently from the
 * deterministic assessment result. A pending coaching request must never
 * block a learner from seeing their completed score and outcome.
 */
public enum AssessmentFeedbackStatus {
    PENDING,
    READY
}
