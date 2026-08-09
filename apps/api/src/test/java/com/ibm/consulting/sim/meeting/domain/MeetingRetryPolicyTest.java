package com.ibm.consulting.sim.meeting.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingRetryPolicyTest {

    @Test
    void permitsThreeLiveMeetingRetriesAfterTheInitialPerformanceFailure() {
        Meeting initialFailure = performanceFailure();

        MeetingRetryEligibility eligibility = MeetingRetryPolicy.eligibilityFor(initialFailure, List.of(initialFailure));

        assertThat(eligibility.available()).isTrue();
        assertThat(eligibility.retriesRemaining()).isEqualTo(3);
    }

    @Test
    void requiresLeadRetryAfterThreePerformanceRetriesAreExhausted() {
        Meeting first = performanceFailure();
        Meeting second = performanceFailure();
        Meeting third = performanceFailure();
        Meeting fourth = performanceFailure();

        MeetingRetryEligibility eligibility = MeetingRetryPolicy.eligibilityFor(
                fourth, List.of(first, second, third, fourth));

        assertThat(eligibility.available()).isFalse();
        assertThat(eligibility.retriesRemaining()).isZero();
    }

    @Test
    void neverPermitsMeetingRetryForUnprofessionalConduct() {
        Meeting conductFailure = Meeting.start(UUID.randomUUID(), UUID.randomUUID());
        conductFailure.complete(MeetingCompletionOutcome.FAILED, "Conduct breach", List.of(),
                MeetingTerminationReason.UNPROFESSIONAL_CONDUCT);

        MeetingRetryEligibility eligibility = MeetingRetryPolicy.eligibilityFor(conductFailure, List.of(conductFailure));

        assertThat(eligibility.available()).isFalse();
    }

    private Meeting performanceFailure() {
        Meeting meeting = Meeting.start(UUID.randomUUID(), UUID.randomUUID());
        meeting.complete(MeetingCompletionOutcome.FAILED, "Meeting gate not met", List.of());
        return meeting;
    }
}
