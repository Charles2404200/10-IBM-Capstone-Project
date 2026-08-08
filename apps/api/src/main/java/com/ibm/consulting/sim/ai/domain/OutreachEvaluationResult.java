package com.ibm.consulting.sim.ai.domain;

/** Structured result of the outreach_evaluation AI use case. */
public record OutreachEvaluationResult(
        String clientReply,
        String outcome,
        int personalisation,
        int relevance,
        int clarity,
        int callToAction,
        int trustDelta,
        int interestDelta) {

    public static OutreachEvaluationResult safeFallback() {
        return new OutreachEvaluationResult(
                "We'll review your message and get back to you.",
                "FOLLOW_UP_REQUIRED", 50, 50, 50, 50, 0, 0);
    }
}
