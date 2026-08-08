package com.ibm.consulting.sim.lead.domain;

/**
 * How reliable the learner judges a piece of evidence to be. Purely a
 * self-assessment captured at collection time — it is not independently
 * verified, but it is the traceability signal the AI assessment engine
 * (Phase 3+) uses when it scores hypothesis quality against cited evidence.
 */
public enum ConfidenceLevel {
    LOW,
    MEDIUM,
    HIGH
}
