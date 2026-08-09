package com.ibm.consulting.sim.outreach.domain;

/** Deterministic rubric result for a client-requested capability brief. */
public record CapabilityBriefReview(
        OutreachOutcome outcome,
        String clientReply,
        int clientFit,
        int industryRelevance,
        int evidenceQuality,
        int clarity,
        int credibility) {}
