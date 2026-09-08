package com.ibm.consulting.sim.identity.application;

/** Storage boundary for login-failure counters. Keys are already normalized and hashed. */
public interface LoginAttemptStore {

    /** Atomically reserves one password-verification attempt for this account. */
    boolean tryAcquire(String key);

    /** Releases a reservation when authentication did not produce an invalid-credentials result. */
    void release(String key);

    int failureCount(String key);

    void reset(String key);
}
