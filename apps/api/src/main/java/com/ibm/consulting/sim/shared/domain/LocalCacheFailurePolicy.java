package com.ibm.consulting.sim.shared.domain;

public enum LocalCacheFailurePolicy
        implements RateLimiterFailurePolicy {

    FAIL_OPEN,
    FAIL_CLOSED
}