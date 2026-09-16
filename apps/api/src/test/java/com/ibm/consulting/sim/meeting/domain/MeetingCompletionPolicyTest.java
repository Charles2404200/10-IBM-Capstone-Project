package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.ai.domain.PersonaStateDelta;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingCompletionPolicyTest {

    @Test
    void passesOnlyWhenEveryRelationshipMetricMeetsTheThreshold() {
        PersonaState state = PersonaState.initial(UUID.randomUUID());
        state.applyClampedDelta(new PersonaStateDelta(30, 30, 30));

        MeetingCompletionDecision decision = MeetingCompletionPolicy.evaluate(state);

        assertThat(decision.outcome()).isEqualTo(MeetingCompletionOutcome.PASSED);
        assertThat(decision.unmetRequirements()).isEmpty();
    }

    @Test
    void failsWhenOneMetricIsBelowTheThreshold() {
        PersonaState state = PersonaState.initial(UUID.randomUUID());
        state.applyClampedDelta(new PersonaStateDelta(30, 30, 19));

        MeetingCompletionDecision decision = MeetingCompletionPolicy.evaluate(state);

        assertThat(decision.outcome()).isEqualTo(MeetingCompletionOutcome.FAILED);
        assertThat(decision.unmetRequirements()).containsExactly("Patience 69/70");
    }

    @Test
    void hardMeetingsRequireEightyAcrossEveryRelationshipMetric() {
        DifficultyProfile hardProfile = DifficultyProfile.defaults(5, 5, 5, 5);
        PersonaState state = PersonaState.initial(UUID.randomUUID());
        state.applyClampedDelta(new PersonaStateDelta(30, 30, 29));

        MeetingCompletionDecision belowGate = MeetingCompletionPolicy.evaluate(state, hardProfile);

        assertThat(belowGate.outcome()).isEqualTo(MeetingCompletionOutcome.FAILED);
        assertThat(belowGate.unmetRequirements()).containsExactly("Patience 79/80");

        state.applyClampedDelta(new PersonaStateDelta(0, 0, 1));

        assertThat(MeetingCompletionPolicy.evaluate(state, hardProfile).outcome())
                .isEqualTo(MeetingCompletionOutcome.PASSED);
    }
}
