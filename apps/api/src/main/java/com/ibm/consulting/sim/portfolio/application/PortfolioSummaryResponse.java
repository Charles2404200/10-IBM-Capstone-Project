package com.ibm.consulting.sim.portfolio.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate view of a learner's training history: overall stats plus how each
 * competency has trended across their completed engagements. Computed purely
 * from persisted {@code Assessment} records — no mock or synthetic data.
 */
public record PortfolioSummaryResponse(
        int totalEngagements,
        int completedEngagements,
        int contractsWon,
        int contractsLost,
        double averageOverallScore,
        List<CompetencyTrend> competencyTrends,
        List<CompletedEngagementView> completedEngagementsHistory) {

    public record CompetencyTrend(String competencyName, List<TrendPoint> points) {
        public record TrendPoint(UUID engagementId, Instant generatedAt, int score) {}
    }

    public record CompletedEngagementView(
            UUID engagementId,
            UUID scenarioId,
            String scenarioTitle,
            String industry,
            String outcome,
            int overallScore,
            Instant completedAt) {}
}
