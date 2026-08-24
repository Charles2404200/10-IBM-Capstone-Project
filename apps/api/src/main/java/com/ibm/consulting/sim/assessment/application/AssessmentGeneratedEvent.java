package com.ibm.consulting.sim.assessment.application;

import com.ibm.consulting.sim.assessment.domain.CompetencyScore;

import java.util.List;
import java.util.UUID;

/** Published after the deterministic assessment transaction commits. */
record AssessmentGeneratedEvent(UUID engagementId, List<CompetencyScore> competencyScores,
                                int overallScore, String outcome) {
    AssessmentGeneratedEvent {
        competencyScores = List.copyOf(competencyScores);
    }
}
