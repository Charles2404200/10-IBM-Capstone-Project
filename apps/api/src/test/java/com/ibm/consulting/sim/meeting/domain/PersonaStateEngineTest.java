package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.ai.domain.PersonaStateDelta;
import com.ibm.consulting.sim.ai.domain.PersonaTurnResponse;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PersonaStateEngineTest {

    @Test
    void appliesClampedDeltaToState() {
        PersonaState state = PersonaState.initial(UUID.randomUUID());
        PersonaTurnResponse turn = new PersonaTurnResponse(
                "Interesting point.", List.of(), new PersonaStateDelta(20, -5, 3),
                List.of(), null, List.of(), new PersonaTurnResponse.SafetyCheck(true, null));

        PersonaStateEngine.apply(state, turn);

        assertThat(state.getTrust()).isEqualTo(60); // 50 + clamp(20 -> 10)
        assertThat(state.getInterest()).isEqualTo(45); // 50 - 5
        assertThat(state.getPatience()).isEqualTo(53); // 50 + 3
    }

    @Test
    void recordsDisclosedFacts() {
        PersonaState state = PersonaState.initial(UUID.randomUUID());
        PersonaTurnResponse turn = new PersonaTurnResponse(
                "We use a legacy ERP.", List.of(), PersonaStateDelta.zero(),
                List.of("fact:legacy-erp", "fact:budget-constraint"), null, List.of(),
                new PersonaTurnResponse.SafetyCheck(true, null));

        PersonaStateEngine.apply(state, turn);

        assertThat(state.getDisclosedFacts()).containsExactlyInAnyOrder("fact:legacy-erp", "fact:budget-constraint");
    }

    @Test
    void nullStateDeltaIsTreatedAsZero() {
        PersonaState state = PersonaState.initial(UUID.randomUUID());
        PersonaTurnResponse turn = new PersonaTurnResponse(
                "Hello.", List.of(), null, List.of(), null, List.of(),
                new PersonaTurnResponse.SafetyCheck(true, null));

        PersonaStateEngine.apply(state, turn);

        assertThat(state.getTrust()).isEqualTo(50);
        assertThat(state.getInterest()).isEqualTo(50);
        assertThat(state.getPatience()).isEqualTo(50);
    }

    @Test
    void trustNeverExceedsUpperBoundAcrossMultipleTurns() {
        PersonaState state = PersonaState.initial(UUID.randomUUID());
        for (int i = 0; i < 10; i++) {
            PersonaTurnResponse turn = new PersonaTurnResponse(
                    "Good point.", List.of(), new PersonaStateDelta(10, 0, 0),
                    List.of(), null, List.of(), new PersonaTurnResponse.SafetyCheck(true, null));
            PersonaStateEngine.apply(state, turn);
        }
        assertThat(state.getTrust()).isEqualTo(100);
    }

    @Test
    void shortTimelineAmplifiesNegativeRelationshipConsequences() {
        PersonaTurnResponse turn = new PersonaTurnResponse(
                "That is too vague for our timeline.", List.of(), new PersonaStateDelta(0, 0, -5),
                List.of(), null, List.of(), new PersonaTurnResponse.SafetyCheck(true, null));
        PersonaState relaxed = PersonaState.initial(UUID.randomUUID(), DifficultyProfile.defaults(1, 1, 1, 1));
        PersonaState urgent = PersonaState.initial(UUID.randomUUID(), DifficultyProfile.defaults(5, 5, 5, 5));

        PersonaStateEngine.apply(relaxed, turn, DifficultyProfile.defaults(1, 1, 1, 1));
        PersonaStateEngine.apply(urgent, turn, DifficultyProfile.defaults(5, 5, 5, 5));

        assertThat(urgent.getPatience()).isLessThan(30);
        assertThat(relaxed.getPatience()).isGreaterThan(70);
    }

    @Test
    void doesNotRewardGreetingOrGenericPromptInLiveMeetingProgression() {
        DifficultyProfile profile = DifficultyProfile.defaults(1, 1, 1, 1);
        PersonaState state = PersonaState.initial(UUID.randomUUID(), profile);
        PersonaTurnResponse overlyPositiveResponse = new PersonaTurnResponse(
                "Good to meet you.", List.of(), new PersonaStateDelta(10, 10, 10),
                List.of(), null, List.of(), new PersonaTurnResponse.SafetyCheck(true, null));

        PersonaStateEngine.apply(state, overlyPositiveResponse, profile, "Hello", 1);
        PersonaStateEngine.apply(state, overlyPositiveResponse, profile, "What do you need to know?", 2);

        assertThat(state.getTrust()).isEqualTo(profile.initialTrust());
        assertThat(state.getInterest()).isEqualTo(profile.initialInterest());
        assertThat(state.getPatience()).isEqualTo(profile.initialPatience());
    }

    @Test
    void capsGroundedProgressionUntilTheMeetingHasEnoughTurns() {
        DifficultyProfile profile = DifficultyProfile.defaults(1, 1, 1, 1);
        PersonaState state = PersonaState.initial(UUID.randomUUID(), profile);
        PersonaTurnResponse overlyPositiveResponse = new PersonaTurnResponse(
                "That is a useful question.", List.of(), new PersonaStateDelta(10, 10, 10),
                List.of(), null, List.of(), new PersonaTurnResponse.SafetyCheck(true, null));

        PersonaStateEngine.apply(state, overlyPositiveResponse, profile,
                "Given the current budget pressure and Q4 timeline, which workflow risk should we validate first?", 1);
        PersonaStateEngine.apply(state, overlyPositiveResponse, profile,
                "You mentioned current integration constraints and operational impact. Which workflow now creates the greatest risk for staff?", 2);

        assertThat(state.getTrust()).isEqualTo(profile.initialTrust() + 3);
        assertThat(state.getInterest()).isEqualTo(profile.initialInterest() + 3);
        assertThat(state.getPatience()).isEqualTo(profile.initialPatience() + 2);
    }
}
