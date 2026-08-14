package com.ibm.consulting.sim.outreach.domain;

import com.ibm.consulting.sim.ai.domain.OutreachEvaluationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutreachContentPolicyTest {
    private final OutreachEvaluationResult optimisticAi = new OutreachEvaluationResult(
            "Let's meet", "ACCEPTED", 95, 95, 95, 95, 5, 5);

    @Test
    void abusiveMessageIsRejectedEvenWhenModelScoresItHighly() {
        OutreachEvaluationResult result = OutreachContentPolicy.apply(optimisticAi, "Quick chat",
                "Hi Sarah, this is fucking ridiculous. Meet with me next week.", "NorthPeak", "Sarah Chen",
                List.of("Sarah owns operational resilience."));

        assertThat(result.outcome()).isEqualTo("REJECTED");
        assertThat(result.personalisation()).isZero();
        assertThat(result.trustDelta()).isNegative();
    }

    @Test
    void lowSignalMessageCannotEarnAnAcceptance() {
        OutreachEvaluationResult result = OutreachContentPolicy.apply(optimisticAi, "Hello", "hello", "NorthPeak",
                "Sarah Chen", List.of("Operational resilience is a priority."));

        assertThat(result.outcome()).isEqualTo("FOLLOW_UP_REQUIRED");
        assertThat(result.relevance()).isLessThanOrEqualTo(20);
    }

    @Test
    void groundedProfessionalMessageRetainsStrongScores() {
        OutreachEvaluationResult result = OutreachContentPolicy.apply(optimisticAi, "Reducing NorthPeak reconciliation delays",
                "Hi Sarah, I noticed the manual reconciliation work affecting NorthPeak operations. We have helped teams reduce reconciliation delays with a phased approach. Would you be open to a 20-minute conversation next week?",
                "NorthPeak", "Sarah Chen", List.of("Manual reconciliation delays are affecting operations."));

        assertThat(result.outcome()).isEqualTo("ACCEPTED");
        assertThat(result.personalisation()).isGreaterThanOrEqualTo(80);
        assertThat(result.callToAction()).isGreaterThanOrEqualTo(80);
    }
}
