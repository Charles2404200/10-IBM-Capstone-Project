package com.ibm.consulting.sim.ai.domain;

/** Relative response-time expectation for a provider, used for capability-based routing decisions. */
public enum LatencyTier {
    LOW,
    MEDIUM,
    HIGH,
    VARIABLE
}
