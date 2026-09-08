package com.ibm.consulting.sim.identity.application;

/** Storage boundary for login-failure counters. Keys are already normalized and hashed. */
public interface LoginAttemptStore {

    int failureCount(String key);

    void recordFailure(String key);

    void reset(String key);
}
