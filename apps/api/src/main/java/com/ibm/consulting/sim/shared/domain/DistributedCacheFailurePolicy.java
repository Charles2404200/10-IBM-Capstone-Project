package com.ibm.consulting.sim.shared.domain;

public enum DistributedCacheFailurePolicy
        implements RateLimiterFailurePolicy {

    FAIL_OPEN,
    FAIL_CLOSED,
    LOCAL_FALLBACK
}