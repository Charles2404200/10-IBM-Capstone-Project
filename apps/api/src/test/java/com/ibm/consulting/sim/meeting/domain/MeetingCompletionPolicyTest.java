package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.ai.domain.PersonaStateDelta;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingCompletionPolicyTest {

    @Test
    void passesOnlyWhenEveryRelationshipMetricMeetsTheThreshold() {
        PersonaState state = PersonaState.initial(UUID.randomUUID());
        state.applyClampedDelta(new PersonaStateDelta(60, 60, 60));

        MeetingCompletionDecision decision = MeetingCompletionPolicy.evaluate(state);

        assertThat(decision.outcome()).isEqualTo(MeetingCompletionOutcome.PASSED);
        assertThat(decision.unmetRequirements()).isEmpty();
    }

    @Test
    void failsWhenOneMetricIsBelowTheThreshold() {
        PersonaState state = PersonaState.initial(UUID.randomUUID());
        state.applyClampedDelta(new PersonaStateDelta(60, 60, 59));

        MeetingCompletionDecision decision = MeetingCompletionPolicy.evaluate(state);

        assertThat(decision.outcome()).isEqualTo(MeetingCompletionOutcome.FAILED);
        assertThat(decision.unmetRequirements()).containsExactly("Patience 69/70");
    }
}
