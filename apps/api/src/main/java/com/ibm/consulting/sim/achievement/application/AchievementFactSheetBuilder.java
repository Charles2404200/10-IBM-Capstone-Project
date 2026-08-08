package com.ibm.consulting.sim.achievement.application;

import com.ibm.consulting.sim.achievement.domain.AchievementFactSheet;
import com.ibm.consulting.sim.assessment.domain.Assessment;
import com.ibm.consulting.sim.assessment.domain.AssessmentRepository;
import com.ibm.consulting.sim.assessment.domain.CompetencyScore;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds an {@link AchievementFactSheet} purely from real persisted engagement and
 * assessment records — the same real-data discipline used by {@code PortfolioService}.
 * Achievement rules are only ever evaluated against actual learner history.
 */
@Component
public class AchievementFactSheetBuilder {

    private static final Set<EngagementState> COMPLETED_STATES = Set.of(
            EngagementState.CONTRACT_WON, EngagementState.CONTRACT_LOST, EngagementState.REVIEW_AVAILABLE);

    private final EngagementRepository engagementRepository;
    private final AssessmentRepository assessmentRepository;

    public AchievementFactSheetBuilder(EngagementRepository engagementRepository,
                                        AssessmentRepository assessmentRepository) {
        this.engagementRepository = engagementRepository;
        this.assessmentRepository = assessmentRepository;
    }

    public AchievementFactSheet build(UUID userId) {
        List<Engagement> engagements = engagementRepository.findByUserId(userId);
        List<Engagement> completed = engagements.stream()
                .filter(e -> COMPLETED_STATES.contains(e.getState()))
                .toList();

        if (completed.isEmpty()) {
            return AchievementFactSheet.empty();
        }

        List<UUID> completedIds = completed.stream().map(Engagement::getId).toList();
        List<Assessment> assessments = assessmentRepository.findAllByEngagementIdIn(completedIds);

        int won = (int) completed.stream().filter(e -> e.getState() == EngagementState.CONTRACT_WON).count();
        int completedCount = completed.size();
        double winRate = completedCount == 0 ? 0.0 : (100.0 * won / completedCount);

        int bestOverall = assessments.stream().mapToInt(Assessment::getOverallScore).max().orElse(0);
        double avgOverall = assessments.stream().mapToInt(Assessment::getOverallScore).average().orElse(0.0);

        int distinctScenarios = (int) completed.stream().map(Engagement::getScenarioId).distinct().count();

        Map<String, Integer> bestCompetencyScores = new HashMap<>();
        for (Assessment assessment : assessments) {
            for (CompetencyScore score : assessment.getCompetencyScores()) {
                bestCompetencyScores.merge(score.getCompetencyName(), score.getScore(), Math::max);
            }
        }

        return new AchievementFactSheet(completedCount, won, bestOverall, avgOverall, distinctScenarios, winRate,
                bestCompetencyScores);
    }
}
