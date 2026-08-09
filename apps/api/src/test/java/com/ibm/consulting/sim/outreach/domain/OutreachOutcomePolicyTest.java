package com.ibm.consulting.sim.outreach.domain;

import com.ibm.consulting.sim.ai.domain.OutreachEvaluationResult;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutreachOutcomePolicyTest {

    @Test
    void cannotAcceptWhenAiSuggestsAcceptanceBelowScenarioGate() {
        DifficultyProfile hard = DifficultyProfile.defaults(5, 5, 5, 5);
        OutreachEvaluationResult evaluation = new OutreachEvaluationResult(
                "Let's meet", "ACCEPTED", 70, 70, 70, 70, 0, 0);

        assertThat(OutreachOutcomePolicy.decide(evaluation, hard)).isEqualTo(OutreachOutcome.FOLLOW_UP_REQUIRED);
    }

    @Test
    void acceptsWhenMessageQualityMeetsScenarioGate() {
        DifficultyProfile easy = DifficultyProfile.defaults(1, 1, 1, 1);
        OutreachEvaluationResult evaluation = new OutreachEvaluationResult(
                "Let's meet", "FOLLOW_UP_REQUIRED", 70, 70, 70, 70, 0, 0);

        assertThat(OutreachOutcomePolicy.decide(evaluation, easy)).isEqualTo(OutreachOutcome.ACCEPTED);
    }
}
