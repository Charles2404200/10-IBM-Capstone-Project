package com.ibm.consulting.sim.engagement.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EngagementPolicyTest {

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @MethodSource("allowedTransitions")
    void allowsEveryImplementedTransition(EngagementState from, EngagementState to) {
        assertThat(EngagementPolicy.canTransitionTo(from, to)).isTrue();
        assertThatNoException().isThrownBy(() -> EngagementPolicy.assertValidTransition(from, to));
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @MethodSource("representativeDisallowedTransitions")
    void rejectsBackwardSkippedAndTerminalTransitions(EngagementState from, EngagementState to) {
        assertThat(EngagementPolicy.canTransitionTo(from, to)).isFalse();
        assertThatThrownBy(() -> EngagementPolicy.assertValidTransition(from, to))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @ParameterizedTest(name = "rejected {0} -> {1} leaves aggregate unchanged")
    @MethodSource("aggregateDisallowedTransitions")
    void rejectedTransitionDoesNotMutateStateOrEvents(EngagementState from, EngagementState to) {
        Engagement engagement = engagementAt(from);
        int eventCount = engagement.getEvents().size();

        assertThatThrownBy(() -> engagement.transitionTo(to, "must not be recorded"))
                .isInstanceOf(InvalidTransitionException.class);

        assertThat(engagement.getState()).isEqualTo(from);
        assertThat(engagement.getEvents()).hasSize(eventCount);
    }

    @Test
    void engagementRecordsEventsOnTransition() {
        Engagement e = Engagement.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThat(e.getState()).isEqualTo(EngagementState.QUALIFYING);
        assertThat(e.getEvents()).hasSize(1);

        e.selectLead(UUID.randomUUID());
        assertThat(e.getState()).isEqualTo(EngagementState.CLIENT_INTELLIGENCE);
        assertThat(e.getEvents()).hasSize(2);
    }

    @Test
    void selectLeadThrowsWhenInvalidState() {
        Engagement e = Engagement.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        e.selectLead(UUID.randomUUID()); // now CLIENT_INTELLIGENCE
        assertThatThrownBy(() -> e.selectLead(UUID.randomUUID()))
                .isInstanceOf(InvalidTransitionException.class);
    }

    private static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(EngagementState.QUALIFYING, EngagementState.CLIENT_INTELLIGENCE),
                Arguments.of(EngagementState.CLIENT_INTELLIGENCE, EngagementState.HYPOTHESIS_READY),
                Arguments.of(EngagementState.HYPOTHESIS_READY, EngagementState.OUTREACHING),
                Arguments.of(EngagementState.OUTREACHING, EngagementState.OUTREACHING),
                Arguments.of(EngagementState.OUTREACHING, EngagementState.MEETING_SECURED),
                Arguments.of(EngagementState.MEETING_SECURED, EngagementState.PREPARING),
                Arguments.of(EngagementState.PREPARING, EngagementState.PREPARING),
                Arguments.of(EngagementState.PREPARING, EngagementState.IN_MEETING),
                Arguments.of(EngagementState.IN_MEETING, EngagementState.DISCOVERY_COMPLETE),
                Arguments.of(EngagementState.IN_MEETING, EngagementState.MEETING_FAILED),
                Arguments.of(EngagementState.DISCOVERY_COMPLETE, EngagementState.PROPOSAL_DRAFT),
                Arguments.of(EngagementState.PROPOSAL_DRAFT, EngagementState.PROPOSAL_SUBMITTED),
                Arguments.of(EngagementState.PROPOSAL_SUBMITTED, EngagementState.CLIENT_DECISION),
                Arguments.of(EngagementState.CLIENT_DECISION, EngagementState.REVIEW),
                Arguments.of(EngagementState.REVIEW, EngagementState.COMPLETED));
    }

    private static Stream<Arguments> representativeDisallowedTransitions() {
        return Stream.of(
                Arguments.of(EngagementState.QUALIFYING, EngagementState.HYPOTHESIS_READY),
                Arguments.of(EngagementState.CLIENT_INTELLIGENCE, EngagementState.QUALIFYING),
                Arguments.of(EngagementState.IN_MEETING, EngagementState.PREPARING),
                Arguments.of(EngagementState.MEETING_FAILED, EngagementState.QUALIFYING),
                Arguments.of(EngagementState.COMPLETED, EngagementState.REVIEW));
    }

    private static Stream<Arguments> aggregateDisallowedTransitions() {
        return Stream.of(
                Arguments.of(EngagementState.QUALIFYING, EngagementState.HYPOTHESIS_READY),
                Arguments.of(EngagementState.CLIENT_INTELLIGENCE, EngagementState.QUALIFYING),
                Arguments.of(EngagementState.MEETING_FAILED, EngagementState.QUALIFYING),
                Arguments.of(EngagementState.COMPLETED, EngagementState.REVIEW));
    }

    private static Engagement engagementAt(EngagementState target) {
        Engagement engagement = Engagement.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        if (target == EngagementState.QUALIFYING) return engagement;
        engagement.selectLead(UUID.randomUUID());
        if (target == EngagementState.CLIENT_INTELLIGENCE) return engagement;
        engagement.transitionTo(EngagementState.HYPOTHESIS_READY, "hypothesis");
        engagement.transitionTo(EngagementState.OUTREACHING, "outreach");
        engagement.transitionTo(EngagementState.MEETING_SECURED, "meeting secured");
        engagement.transitionTo(EngagementState.PREPARING, "preparing");
        engagement.transitionTo(EngagementState.IN_MEETING, "meeting");
        if (target == EngagementState.MEETING_FAILED) {
            engagement.transitionTo(EngagementState.MEETING_FAILED, "failed");
            return engagement;
        }
        engagement.transitionTo(EngagementState.DISCOVERY_COMPLETE, "discovery");
        engagement.transitionTo(EngagementState.PROPOSAL_DRAFT, "draft");
        engagement.transitionTo(EngagementState.PROPOSAL_SUBMITTED, "submitted");
        engagement.transitionTo(EngagementState.CLIENT_DECISION, "decision");
        engagement.transitionTo(EngagementState.REVIEW, "review");
        engagement.transitionTo(EngagementState.COMPLETED, "completed");
        return engagement;
    }
}
