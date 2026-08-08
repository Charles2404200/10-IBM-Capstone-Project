package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.ai.domain.PersonaStateDelta;
import com.ibm.consulting.sim.ai.domain.PersonaTurnResponse;
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
}
