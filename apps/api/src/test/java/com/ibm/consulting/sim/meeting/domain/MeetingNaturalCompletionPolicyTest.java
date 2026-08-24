package com.ibm.consulting.sim.meeting.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingNaturalCompletionPolicyTest {

    @Test
    void concludesOnlyAfterGateMinimumDiscoveryAndClientClosureSignal() {
        PersonaState state = PersonaState.initial(java.util.UUID.randomUUID());
        state.applyClampedDelta(new com.ibm.consulting.sim.ai.domain.PersonaStateDelta(40, 40, 40));

        assertThat(MeetingNaturalCompletionPolicy.shouldConclude(state, List.of("client_ready_to_close"), 2)).isFalse();
        assertThat(MeetingNaturalCompletionPolicy.shouldConclude(state, List.of("client_concern_resolved"), 3)).isFalse();
        assertThat(MeetingNaturalCompletionPolicy.shouldConclude(state, List.of("client_ready_to_close"), 3)).isTrue();
    }
}
