package com.ibm.consulting.sim.meeting.application;

import com.ibm.consulting.sim.scenario.domain.DifficultyLevel;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuidedResponseBalancePolicyTest {

    @Test
    void addsOneCredibleMisstepForEasyGuidance() {
        List<String> options = GuidedResponseBalancePolicy.balance(threeStrongOptions(), profile(DifficultyLevel.EASY));

        assertThat(options).anyMatch(option -> option.contains("I do not have enough detail"));
    }

    @Test
    void addsTwoCredibleMisstepsForMediumGuidance() {
        List<String> options = GuidedResponseBalancePolicy.balance(threeStrongOptions(), profile(DifficultyLevel.MEDIUM));

        assertThat(options).anyMatch(option -> option.contains("I do not have enough detail"));
        assertThat(options).anyMatch(option -> option.contains("less important"));
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
