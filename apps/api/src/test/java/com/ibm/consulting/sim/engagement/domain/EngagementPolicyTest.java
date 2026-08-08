package com.ibm.consulting.sim.engagement.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EngagementPolicyTest {

    @Test
    void allowsAllLegalTransitions() {
        assertThatNoException().isThrownBy(() -> {
            EngagementPolicy.assertValidTransition(EngagementState.DRAFT, EngagementState.LEAD_SELECTED);
            EngagementPolicy.assertValidTransition(EngagementState.LEAD_SELECTED, EngagementState.RESEARCH_COMPLETED);
            EngagementPolicy.assertValidTransition(EngagementState.RESEARCH_COMPLETED, EngagementState.OUTREACH_IN_PROGRESS);
            EngagementPolicy.assertValidTransition(EngagementState.OUTREACH_IN_PROGRESS, EngagementState.MEETING_SECURED);
            EngagementPolicy.assertValidTransition(EngagementState.OUTREACH_IN_PROGRESS, EngagementState.OUTREACH_FAILED);
            EngagementPolicy.assertValidTransition(EngagementState.MEETING_SECURED, EngagementState.PREPARATION_COMPLETED);
            EngagementPolicy.assertValidTransition(EngagementState.PREPARATION_COMPLETED, EngagementState.MEETING_IN_PROGRESS);
            EngagementPolicy.assertValidTransition(EngagementState.MEETING_IN_PROGRESS, EngagementState.MEETING_COMPLETED);
            EngagementPolicy.assertValidTransition(EngagementState.MEETING_COMPLETED, EngagementState.PROPOSAL_SUBMITTED);
            EngagementPolicy.assertValidTransition(EngagementState.PROPOSAL_SUBMITTED, EngagementState.CONTRACT_WON);
            EngagementPolicy.assertValidTransition(EngagementState.PROPOSAL_SUBMITTED, EngagementState.CONTRACT_LOST);
            EngagementPolicy.assertValidTransition(EngagementState.CONTRACT_WON, EngagementState.REVIEW_AVAILABLE);
            EngagementPolicy.assertValidTransition(EngagementState.REVIEW_AVAILABLE, EngagementState.ARCHIVED);
        });
    }

    @Test
    void rejectsIllegalTransitions() {
        assertThatThrownBy(() -> EngagementPolicy.assertValidTransition(EngagementState.DRAFT, EngagementState.CONTRACT_WON))
                .isInstanceOf(InvalidTransitionException.class);
        assertThatThrownBy(() -> EngagementPolicy.assertValidTransition(EngagementState.ARCHIVED, EngagementState.DRAFT))
                .isInstanceOf(InvalidTransitionException.class);
        assertThatThrownBy(() -> EngagementPolicy.assertValidTransition(EngagementState.MEETING_IN_PROGRESS, EngagementState.DRAFT))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void engagementRecordsEventsOnTransition() {
        Engagement e = Engagement.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThat(e.getState()).isEqualTo(EngagementState.DRAFT);
        assertThat(e.getEvents()).hasSize(1);

        e.selectLead(UUID.randomUUID());
        assertThat(e.getState()).isEqualTo(EngagementState.LEAD_SELECTED);
        assertThat(e.getEvents()).hasSize(2);
    }

    @Test
    void selectLeadThrowsWhenInvalidState() {
        Engagement e = Engagement.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        e.selectLead(UUID.randomUUID()); // now LEAD_SELECTED
        assertThatThrownBy(() -> e.selectLead(UUID.randomUUID()))
                .isInstanceOf(InvalidTransitionException.class);
    }
}
