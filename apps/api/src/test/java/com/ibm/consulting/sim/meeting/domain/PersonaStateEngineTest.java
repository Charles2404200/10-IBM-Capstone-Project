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
    void capsLegacyScenarioStartingScoresAtTheCurrentGameplayBaseline() {
        DifficultyProfile legacyProfile = new DifficultyProfile(
                DifficultyLevel.EASY,
                4, 1, 0, 65, 65, 75, 14, true, 30,
                2, 40, 65, 50, 20, 115);

        PersonaState state = PersonaState.initial(UUID.randomUUID(), legacyProfile);

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

        assertThat(urgent.getPatience()).isLessThan(relaxed.getPatience());
        assertThat(relaxed.getPatience()).isLessThan(50);
    }

    @Test
    void raisesLegacyFortyPointSnapshotsToTheCurrentMeetingBaseline() {
        DifficultyProfile legacyProfile = new DifficultyProfile(
                DifficultyLevel.MEDIUM,
                3, 2, 1, 40, 40, 40, 12, true, 24,
                2, 40, 65, 50, 20, 115);

        PersonaState state = PersonaState.initial(UUID.randomUUID(), legacyProfile);

        assertThat(state.getTrust()).isEqualTo(50);
        assertThat(state.getInterest()).isEqualTo(50);
        assertThat(state.getPatience()).isEqualTo(50);
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

        assertThat(state.getTrust()).isEqualTo(45);
        assertThat(state.getInterest()).isEqualTo(46);
        assertThat(state.getPatience()).isEqualTo(47);
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

        assertThat(state.getTrust()).isEqualTo(62);
        assertThat(state.getInterest()).isEqualTo(62);
        assertThat(state.getPatience()).isEqualTo(64);
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

        assertThat(state.getTrust()).isEqualTo(36);
        assertThat(state.getInterest()).isEqualTo(38);
        assertThat(state.getPatience()).isEqualTo(40);
    }

    @Test
    void penalizesAPrematureRecommendationEvenWhenTheProviderSuggestsAPositiveDelta() {
        DifficultyProfile profile = DifficultyProfile.defaults(3, 3, 3, 3);
        PersonaState state = PersonaState.initial(UUID.randomUUID(), profile);
        PersonaTurnResponse incorrectlyPositiveResponse = new PersonaTurnResponse(
                "The client is frustrated.", List.of(), new PersonaStateDelta(10, 10, 10),
                List.of(), null, List.of(), new PersonaTurnResponse.SafetyCheck(true, null));

        PersonaStateEngine.apply(state, incorrectlyPositiveResponse, profile,
                "The exact constraint is less important, so I suggest moving straight to a broader recommendation.", 1);

        assertThat(state.getTrust()).isLessThan(50);
        assertThat(state.getInterest()).isLessThan(50);
        assertThat(state.getPatience()).isLessThan(50);
    }

    @Test
    void penalizesTheGuidedEvasiveChoiceEvenWhenTheProviderSuggestsAPositiveDelta() {
        DifficultyProfile profile = DifficultyProfile.defaults(1, 1, 1, 1);
        PersonaState state = PersonaState.initial(UUID.randomUUID(), profile);
        PersonaTurnResponse incorrectlyPositiveResponse = new PersonaTurnResponse(
                "The client is frustrated.", List.of(), new PersonaStateDelta(10, 10, 10),
                List.of(), null, List.of(), new PersonaTurnResponse.SafetyCheck(true, null));

        PersonaStateEngine.apply(state, incorrectlyPositiveResponse, profile,
                "I do not have enough detail to address that concern today, so perhaps we should revisit it later.", 1);

        assertThat(state.getTrust()).isLessThan(50);
        assertThat(state.getInterest()).isLessThan(50);
        assertThat(state.getPatience()).isLessThan(50);
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

    @Test
    void usesAiAssessmentAsBoundedSupportingEvidenceForVerifiedBehaviour() {
        DifficultyProfile profile = DifficultyProfile.defaults(3, 3, 3, 3);
        String message = "Given the current integration risk and audit timeline, I would validate the medication handoff first. Which reconciliation delay has the greatest operational impact?";
        List<String> labels = List.of("directly_addresses_concern", "uses_client_fact", "asks_focused_question");
        PersonaState withAiSupport = PersonaState.initial(UUID.randomUUID(), profile);
        PersonaState withoutAiSupport = PersonaState.initial(UUID.randomUUID(), profile);

        PersonaStateEngine.apply(withAiSupport, new PersonaTurnResponse(
                "That is a useful starting point.", labels, new PersonaStateDelta(10, 10, 10),
                List.of(), null, List.of(), new PersonaTurnResponse.SafetyCheck(true, null)), profile, message, 3);
        PersonaStateEngine.apply(withoutAiSupport, new PersonaTurnResponse(
                "That is a useful starting point.", labels, PersonaStateDelta.zero(),
                List.of(), null, List.of(), new PersonaTurnResponse.SafetyCheck(true, null)), profile, message, 3);

        assertThat(withAiSupport.getTrust()).isGreaterThan(withoutAiSupport.getTrust());
        assertThat(withAiSupport.getInterest()).isGreaterThan(withoutAiSupport.getInterest());
        assertThat(withAiSupport.getPatience()).isGreaterThan(withoutAiSupport.getPatience());
    }

    @Test
    void resetsOnlyTheLiveMeetingRelationshipStateForARetry() {
        DifficultyProfile profile = DifficultyProfile.defaults(3, 3, 3, 3);
        PersonaState state = PersonaState.initial(UUID.randomUUID(), profile);
        state.applyClampedDelta(new PersonaStateDelta(20, 20, 20));
        state.disclose("fact:budget-pressure");

        state.reset(profile);

        assertThat(state.getTrust()).isEqualTo(50);
        assertThat(state.getInterest()).isEqualTo(50);
        assertThat(state.getPatience()).isEqualTo(50);
        assertThat(state.getDisclosedFacts()).isEmpty();
    }

    @Test
    void restoresPatienceForConsistentlyGroundedClientCentricResponses() {
        DifficultyProfile profile = DifficultyProfile.defaults(3, 3, 3, 3);
        PersonaState state = PersonaState.initial(UUID.randomUUID(), profile);
        String message = "I understand the audit deadline and would protect the medication handoff first. We can measure reconciliation delays, validate rollback criteria, and agree a focused pilot scope. Which clinical workflow should we validate with your team?";
        PersonaTurnResponse strongResponse = new PersonaTurnResponse(
                "That is a credible approach.",
                List.of("directly_addresses_concern", "acknowledges_constraint", "uses_client_fact",
                        "uses_specific_metric", "asks_focused_question", "grounded_recommendation"),
                new PersonaStateDelta(10, 10, 10), List.of(), null, List.of(),
                new PersonaTurnResponse.SafetyCheck(true, null));

        PersonaStateEngine.apply(state, strongResponse, profile, message, 1);
        PersonaStateEngine.apply(state, strongResponse, profile, message, 2);
        PersonaStateEngine.apply(state, strongResponse, profile, message, 3);

        assertThat(state.getPatience()).isGreaterThanOrEqualTo(70);
    }

    @Test
    void convertsExplicitClientCommitmentIntoMeaningfulProgression() {
        DifficultyProfile profile = DifficultyProfile.defaults(3, 3, 3, 3);
        PersonaState state = PersonaState.initial(UUID.randomUUID(), profile);
        List<String> messages = List.of(
                "Given the current stockout risk and delivery timeline, I would validate the Atlanta workflow first. Which exception creates the greatest operational impact?",
                "To protect the pilot budget and operational workflow, we would measure stockouts, order delays, and reconciliation effort. Does that create the board evidence you need?",
                "With the Atlanta team as product owner, we can agree a Week 3 validation gate for the existing SAP and WMS data. Would that reduce delivery risk?",
                "I will send a fixed-fee proposal for the Atlanta pilot, including the success metrics, delivery ownership, and go or no-go criteria. Can we confirm the approval path today?");
        List<String> clientResponses = List.of(
                "That is more like it. You have my attention for the Atlanta pilot.",
                "That would be fast enough to demonstrate value to the board.",
                "Yes, we can agree that a Week 3 validation gate reduces the operational risk.",
                "Perfect. We are comfortable authorizing the first tranche. Let's get this moving and start tomorrow.");

        for (int index = 0; index < messages.size(); index++) {
            PersonaStateEngine.apply(state, new PersonaTurnResponse(
                    clientResponses.get(index), List.of(), PersonaStateDelta.zero(), List.of(), null,
                    List.of(index == messages.size() - 1 ? "client_committed_next_step" : "client_validated_value"),
                    new PersonaTurnResponse.SafetyCheck(true, null)), profile, messages.get(index), index + 1);
        }

        assertThat(state.getTrust()).isGreaterThanOrEqualTo(MeetingCompletionPolicy.REQUIRED_SCORE);
        assertThat(state.getInterest()).isGreaterThanOrEqualTo(MeetingCompletionPolicy.REQUIRED_SCORE);
        assertThat(state.getPatience()).isGreaterThanOrEqualTo(MeetingCompletionPolicy.REQUIRED_SCORE);
        assertThat(MeetingCompletionPolicy.evaluate(state).passed()).isTrue();
    }

    @Test
    void doesNotAwardAClientCommitmentForAnUngroundedGreeting() {
        DifficultyProfile profile = DifficultyProfile.defaults(3, 3, 3, 3);
        PersonaState state = PersonaState.initial(UUID.randomUUID(), profile);

        PersonaStateEngine.apply(state, new PersonaTurnResponse(
                "Perfect. Let's get this moving and start tomorrow.", List.of(), new PersonaStateDelta(10, 10, 10),
                List.of(), null, List.of("client_committed_next_step"),
                new PersonaTurnResponse.SafetyCheck(true, null)), profile, "Hello there", 1);

        assertThat(state.getTrust()).isEqualTo(50);
        assertThat(state.getInterest()).isEqualTo(50);
        assertThat(state.getPatience()).isEqualTo(50);
    }
}
