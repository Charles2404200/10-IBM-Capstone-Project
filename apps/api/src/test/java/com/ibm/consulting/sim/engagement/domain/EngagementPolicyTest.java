package com.ibm.consulting.sim.engagement.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EngagementPolicyTest {

    @Test
    void allowsAllLegalTransitions() {
        assertThatNoException().isThrownBy(() -> {
            EngagementPolicy.assertValidTransition(EngagementState.QUALIFYING, EngagementState.CLIENT_INTELLIGENCE);
            EngagementPolicy.assertValidTransition(EngagementState.CLIENT_INTELLIGENCE, EngagementState.HYPOTHESIS_READY);
            EngagementPolicy.assertValidTransition(EngagementState.HYPOTHESIS_READY, EngagementState.OUTREACHING);
            EngagementPolicy.assertValidTransition(EngagementState.OUTREACHING, EngagementState.MEETING_SECURED);
            EngagementPolicy.assertValidTransition(EngagementState.OUTREACHING, EngagementState.OUTREACHING);
            EngagementPolicy.assertValidTransition(EngagementState.MEETING_SECURED, EngagementState.PREPARING);
            EngagementPolicy.assertValidTransition(EngagementState.PREPARING, EngagementState.IN_MEETING);
            EngagementPolicy.assertValidTransition(EngagementState.IN_MEETING, EngagementState.DISCOVERY_COMPLETE);
            EngagementPolicy.assertValidTransition(EngagementState.DISCOVERY_COMPLETE, EngagementState.PROPOSAL_DRAFT);
            EngagementPolicy.assertValidTransition(EngagementState.PROPOSAL_DRAFT, EngagementState.PROPOSAL_SUBMITTED);
            EngagementPolicy.assertValidTransition(EngagementState.PROPOSAL_SUBMITTED, EngagementState.CLIENT_DECISION);
            EngagementPolicy.assertValidTransition(EngagementState.CLIENT_DECISION, EngagementState.REVIEW);
            EngagementPolicy.assertValidTransition(EngagementState.REVIEW, EngagementState.COMPLETED);
        });
    }

    @Test
    void rejectsIllegalTransitions() {
        assertThatThrownBy(() -> EngagementPolicy.assertValidTransition(EngagementState.QUALIFYING, EngagementState.CLIENT_DECISION))
                .isInstanceOf(InvalidTransitionException.class);
        assertThatThrownBy(() -> EngagementPolicy.assertValidTransition(EngagementState.COMPLETED, EngagementState.QUALIFYING))
                .isInstanceOf(InvalidTransitionException.class);
        assertThatThrownBy(() -> EngagementPolicy.assertValidTransition(EngagementState.IN_MEETING, EngagementState.QUALIFYING))
                .isInstanceOf(InvalidTransitionException.class);
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
}
