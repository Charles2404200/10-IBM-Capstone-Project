package com.ibm.consulting.sim.outreach.domain;

import com.ibm.consulting.sim.ai.domain.OutreachEvaluationResult;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;

/**
 * Deterministic gate for progressing an engagement after outreach. The model
 * classifies message qualities and writes language; it never selects state.
 */
public final class OutreachOutcomePolicy {

    private OutreachOutcomePolicy() {}

    public static OutreachOutcome decide(OutreachEvaluationResult evaluation, DifficultyProfile profile) {
        int averageQuality = Math.round((evaluation.personalisation() + evaluation.relevance()
                + evaluation.clarity() + evaluation.callToAction()) / 4.0f);
        if (averageQuality >= profile.outreachAcceptanceThreshold()) return OutreachOutcome.ACCEPTED;
        if (averageQuality < 45 && "REJECTED".equalsIgnoreCase(evaluation.outcome())) return OutreachOutcome.REJECTED;
        return OutreachOutcome.FOLLOW_UP_REQUIRED;
    }
}
