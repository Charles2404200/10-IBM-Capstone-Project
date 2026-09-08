package com.ibm.consulting.sim.identity.application;

import java.time.Duration;

public class LoginRateLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    public LoginRateLimitExceededException(Duration retryAfter) {
        super("Too many login attempts. Try again later.");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
