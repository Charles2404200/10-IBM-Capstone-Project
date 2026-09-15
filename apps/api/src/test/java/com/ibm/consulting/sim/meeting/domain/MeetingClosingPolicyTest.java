package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.ai.domain.PersonaStateDelta;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingClosingPolicyTest {

    @Test
    void requiresFinalConfirmationOnlyAfterTheRelationshipGateWasAlreadyMet() {
        PersonaState state = PersonaState.initial(UUID.randomUUID());
        state.applyClampedDelta(new PersonaStateDelta(30, 30, 30));

        assertThat(MeetingClosingPolicy.requiresConclusionAfterReply(state, 2)).isFalse();
        assertThat(MeetingClosingPolicy.requiresConclusionAfterReply(state, 3)).isTrue();
        assertThat(MeetingClosingPolicy.canConclude(state, true, 4)).isTrue();
    }

    @Test
    void doesNotConcludeWhenFinalReplyDropsBelowTheGate() {
        PersonaState state = PersonaState.initial(UUID.randomUUID());
        state.applyClampedDelta(new PersonaStateDelta(30, 30, 30));
        state.applyClampedDelta(new PersonaStateDelta(-20, 0, 0));

        assertThat(MeetingClosingPolicy.canConclude(state, true, 4)).isFalse();
    }

    @Test
    void hardMeetingDoesNotEnterTheClosingExchangeAtSeventyNine() {
        DifficultyProfile hardProfile = DifficultyProfile.defaults(5, 5, 5, 5);
        PersonaState state = PersonaState.initial(UUID.randomUUID());
        state.applyClampedDelta(new PersonaStateDelta(30, 30, 29));

        assertThat(MeetingClosingPolicy.requiresConclusionAfterReply(state, hardProfile, 3)).isFalse();

        state.applyClampedDelta(new PersonaStateDelta(0, 0, 1));

        assertThat(MeetingClosingPolicy.requiresConclusionAfterReply(state, hardProfile, 3)).isTrue();
    }
}
