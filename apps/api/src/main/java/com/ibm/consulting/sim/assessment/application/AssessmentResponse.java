package com.ibm.consulting.sim.assessment.application;

import com.ibm.consulting.sim.assessment.domain.Assessment;
import com.ibm.consulting.sim.assessment.domain.CompetencyScore;

import java.time.Instant;
import java.util.List;
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
                a.getStrengths(), a.getImprovementAreas(), a.getGeneratedAt());
    }
}
