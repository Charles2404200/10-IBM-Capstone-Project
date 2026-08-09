package com.ibm.consulting.sim.meeting.domain;

/** Deterministic retry decision for a completed meeting attempt. */
public record MeetingRetryEligibility(boolean available, int retriesRemaining) {

    public static final int MAX_PERFORMANCE_RETRIES = 3;

    public static MeetingRetryEligibility unavailable() {
        return new MeetingRetryEligibility(false, 0);
    }

    public static MeetingRetryEligibility forPerformanceFailureCount(int failureCount) {
        int retriesUsed = Math.max(0, failureCount - 1);
        int remaining = Math.max(0, MAX_PERFORMANCE_RETRIES - retriesUsed);
        return new MeetingRetryEligibility(remaining > 0, remaining);
    }
}
