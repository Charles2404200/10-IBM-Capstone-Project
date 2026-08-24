package com.ibm.consulting.sim.meeting.application;

import com.ibm.consulting.sim.scenario.domain.DifficultyLevel;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuidedResponseBalancePolicyTest {

    @Test
    void addsOneProfessionalNearMissForEasyGuidance() {
        List<String> options = GuidedResponseBalancePolicy.balance(threeStrongOptions(), profile(DifficultyLevel.EASY), 1);

        assertThat(options).hasSize(3);
        assertThat(options.stream().filter(GuidedResponseBalancePolicy::isNearMiss)).hasSize(1);
        assertThat(options).noneMatch(option -> option.contains("I do not have enough detail"));
    }

    @Test
    void addsTwoProfessionalNearMissesForMediumGuidance() {
        List<String> options = GuidedResponseBalancePolicy.balance(threeStrongOptions(), profile(DifficultyLevel.MEDIUM), 2);

        assertThat(options).hasSize(3);
        assertThat(options.stream().filter(GuidedResponseBalancePolicy::isNearMiss)).hasSize(2);
        assertThat(options).noneMatch(option -> option.contains("I do not have enough detail"));
    }

    @Test
    void removesAnExtraNearMissFromEasyGuidance() {
        List<String> options = GuidedResponseBalancePolicy.balance(List.of(
                "Based on the direction so far, I would frame the next phase around a focused pilot and refine the remaining operational detail as the work begins.",
                "To keep momentum, I would prioritise the technical workstream first and leave the operating constraints for the implementation plan.",
                threeStrongOptions().get(0)), profile(DifficultyLevel.EASY), 0);

        assertThat(options.stream().filter(GuidedResponseBalancePolicy::isNearMiss)).hasSize(1);
        assertThat(options).anyMatch(option -> option.startsWith("Before we commit scope"));
    }

    @Test
    void rotatesOptionPlacementBySourceTurnWithoutChangingTheChoiceSet() {
        List<String> generated = List.of(
                threeStrongOptions().get(0),
                threeStrongOptions().get(1),
                "Based on the direction so far, I would frame the next phase around a focused pilot and refine the remaining operational detail as the work begins.");
        List<String> firstTurn = GuidedResponseBalancePolicy.balance(generated, profile(DifficultyLevel.EASY), 1);
        List<String> secondTurn = GuidedResponseBalancePolicy.balance(generated, profile(DifficultyLevel.EASY), 2);

        assertThat(secondTurn).containsExactlyInAnyOrderElementsOf(firstTurn);
        assertThat(secondTurn).isNotEqualTo(firstTurn);
    }

    private List<String> threeStrongOptions() {
        return List.of(
                "Could we confirm which workflow creates the largest operational impact before deciding the pilot scope?",
                "Based on the current risk, could we agree one measurable outcome for the initial discovery step?",
                "Would it help to identify the accountable owner and approval constraint before recommending a next step?");
    }

    private DifficultyProfile profile(DifficultyLevel level) {
        return DifficultyProfile.defaults(level == DifficultyLevel.EASY ? 1 : 3, 3, 3, 3);
    }
}
