package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.ai.domain.PersonaStateDelta;
import com.ibm.consulting.sim.ai.domain.PersonaTurnResponse;
import com.ibm.consulting.sim.scenario.domain.DifficultyLevel;
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

        assertThat(state.getTrust()).isEqualTo(20); // 10 + clamp(20 -> 10)
        assertThat(state.getInterest()).isEqualTo(5); // 10 - 5
        assertThat(state.getPatience()).isEqualTo(13); // 10 + 3
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

        assertThat(state.getTrust()).isEqualTo(10);
        assertThat(state.getInterest()).isEqualTo(10);
        assertThat(state.getPatience()).isEqualTo(10);
    }

    @Test
    void capsLegacyScenarioStartingScoresAtEarnedTrustBaseline() {
        DifficultyProfile legacyProfile = new DifficultyProfile(
                DifficultyLevel.EASY,
                4, 1, 0, 65, 65, 75, 14, true, 30,
                2, 40, 65, 50, 20, 115);

        PersonaState state = PersonaState.initial(UUID.randomUUID(), legacyProfile);

        assertThat(state.getTrust()).isEqualTo(10);
        assertThat(state.getInterest()).isEqualTo(10);
        assertThat(state.getPatience()).isEqualTo(10);
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

        assertThat(urgent.getPatience()).isLessThan(relaxed.getPatience());
        assertThat(relaxed.getPatience()).isLessThan(10);
    }

    @Test
    void treatsGreetingsAsNeutralAndGenericDeflectionAsNegative() {
        DifficultyProfile profile = DifficultyProfile.defaults(1, 1, 1, 1);
        PersonaState state = PersonaState.initial(UUID.randomUUID(), profile);
        PersonaTurnResponse overlyPositiveResponse = new PersonaTurnResponse(
                "Good to meet you.", List.of(), new PersonaStateDelta(10, 10, 10),
                List.of(), null, List.of(), new PersonaTurnResponse.SafetyCheck(true, null));

        PersonaStateEngine.apply(state, overlyPositiveResponse, profile, "Hello", 1);
        PersonaStateEngine.apply(state, overlyPositiveResponse, profile, "What do you need to know?", 2);

        assertThat(state.getTrust()).isEqualTo(5);
        assertThat(state.getInterest()).isEqualTo(6);
        assertThat(state.getPatience()).isEqualTo(7);
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

        assertThat(state.getTrust()).isEqualTo(13);
        assertThat(state.getInterest()).isEqualTo(13);
        assertThat(state.getPatience()).isEqualTo(12);
    }

    @Test
    void penalizesAnUnpreparedResponseEvenWhenTheProviderSuggestsAPositiveDelta() {
        DifficultyProfile profile = DifficultyProfile.defaults(1, 1, 1, 1);
        PersonaState state = PersonaState.initial(UUID.randomUUID(), profile);
        PersonaTurnResponse incorrectlyPositiveResponse = new PersonaTurnResponse(
                "The client is frustrated.", List.of(), new PersonaStateDelta(10, 10, 10),
                List.of(), null, List.of(), new PersonaTurnResponse.SafetyCheck(true, null));

        PersonaStateEngine.apply(state, incorrectlyPositiveResponse, profile,
                "I dont know what ur talking about", 1);

        assertThat(state.getTrust()).isZero();
        assertThat(state.getInterest()).isZero();
        assertThat(state.getPatience()).isZero();
    }

    @Test
    void rewardsGroundedBehaviourLabelsInsteadOfAnUnboundedModelDelta() {
        DifficultyProfile profile = DifficultyProfile.defaults(1, 1, 1, 1);
        PersonaState scoredByBehaviour = PersonaState.initial(UUID.randomUUID(), profile);
        PersonaState withoutBehaviourEvidence = PersonaState.initial(UUID.randomUUID(), profile);
        String message = "Given the current budget pressure and Q4 timeline, which workflow risk should we validate first?";

        PersonaTurnResponse evidenceGroundedTurn = new PersonaTurnResponse(
                "That is a useful question.",
                List.of("directly_addresses_concern", "uses_client_fact", "quantifies_business_impact", "asks_focused_question"),
                new PersonaStateDelta(10, 10, 10), List.of(), null, List.of(),
                new PersonaTurnResponse.SafetyCheck(true, null));
        PersonaTurnResponse noBehaviourEvidenceTurn = new PersonaTurnResponse(
                "That is a useful question.", List.of(), new PersonaStateDelta(10, 10, 10),
                List.of(), null, List.of(), new PersonaTurnResponse.SafetyCheck(true, null));

        PersonaStateEngine.apply(scoredByBehaviour, evidenceGroundedTurn, profile, message, 4);
        PersonaStateEngine.apply(withoutBehaviourEvidence, noBehaviourEvidenceTurn, profile, message, 4);

        assertThat(scoredByBehaviour.getTrust()).isGreaterThan(withoutBehaviourEvidence.getTrust());
        assertThat(scoredByBehaviour.getInterest()).isGreaterThan(withoutBehaviourEvidence.getInterest());
    }
}
