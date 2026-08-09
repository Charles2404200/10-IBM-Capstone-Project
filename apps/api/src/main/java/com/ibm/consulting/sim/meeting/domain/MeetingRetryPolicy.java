package com.ibm.consulting.sim.meeting.domain;

import java.util.List;

/** Keeps retry entitlement independent of the UI and AI provider output. */
public final class MeetingRetryPolicy {

    private MeetingRetryPolicy() {
    }

    public static MeetingRetryEligibility eligibilityFor(Meeting meeting, List<Meeting> attempts) {
        if (!isPerformanceFailure(meeting)) {
            return MeetingRetryEligibility.unavailable();
        }
        int performanceFailureCount = (int) attempts.stream()
                .filter(MeetingRetryPolicy::isPerformanceFailure)
                .count();
        return MeetingRetryEligibility.forPerformanceFailureCount(performanceFailureCount);
    }

    public static boolean isPerformanceFailure(Meeting meeting) {
        return meeting.getStatus() == MeetingStatus.COMPLETED
                && meeting.getCompletionOutcome() == MeetingCompletionOutcome.FAILED
                && meeting.getTerminationReason() != MeetingTerminationReason.UNPROFESSIONAL_CONDUCT;
    }
}
