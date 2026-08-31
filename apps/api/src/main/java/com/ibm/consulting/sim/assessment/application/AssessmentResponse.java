package com.ibm.consulting.sim.assessment.application;

import com.ibm.consulting.sim.assessment.domain.Assessment;
import com.ibm.consulting.sim.assessment.domain.CompetencyScore;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AssessmentResponse(
        UUID id,
        UUID engagementId,
        List<CompetencyScoreView> competencyScores,
        int overallScore,
        String outcome,
        String feedbackSummary,
        List<String> strengths,
        List<String> improvementAreas,
        boolean coachingPending,
        Instant generatedAt) {

    public record CompetencyScoreView(String name, int score, String evidenceNote) {
        static CompetencyScoreView from(CompetencyScore s) {
            return new CompetencyScoreView(s.getCompetencyName(), s.getScore(), s.getEvidenceNote());
        }
    }

    public static AssessmentResponse from(Assessment a) {
        return new AssessmentResponse(a.getId(), a.getEngagementId(),
                a.getCompetencyScores().stream().map(CompetencyScoreView::from).toList(),
                a.getOverallScore(), a.getOutcome(), a.getFeedbackSummary(),
                copiedStrings(a.getStrengths()), copiedStrings(a.getImprovementAreas()),
                a.getFeedbackStatus() == com.ibm.consulting.sim.assessment.domain.AssessmentFeedbackStatus.PENDING,
                a.getGeneratedAt());
    }

    /**
     * Never expose a Hibernate persistent collection from an API DTO. Jackson
     * serializes after the transaction is closed, so the response must own a
     * materialized snapshot of each learner-facing collection.
     */
    private static List<String> copiedStrings(List<String> values) {
        return values.stream().filter(Objects::nonNull).toList();
    }
}
