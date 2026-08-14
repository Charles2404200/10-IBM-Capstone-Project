package com.ibm.consulting.sim.portfolio.application;

import com.ibm.consulting.sim.assessment.domain.Assessment;
import com.ibm.consulting.sim.assessment.domain.AssessmentRepository;
import com.ibm.consulting.sim.assessment.domain.CompetencyScore;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.portfolio.application.PortfolioSummaryResponse.CompetencyTrend;
import com.ibm.consulting.sim.portfolio.application.PortfolioSummaryResponse.CompetencyTrend.TrendPoint;
import com.ibm.consulting.sim.portfolio.application.PortfolioSummaryResponse.CompletedEngagementView;
import com.ibm.consulting.sim.portfolio.application.ReplayComparisonResponse.CompetencyScoreView;
import com.ibm.consulting.sim.portfolio.application.ReplayComparisonResponse.EngagementSnapshot;
import com.ibm.consulting.sim.scenario.domain.Persona;
import com.ibm.consulting.sim.scenario.domain.PersonaRepository;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.shared.domain.DomainException;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.ibm.consulting.sim.shared.config.CacheConfig.PORTFOLIO_SUMMARY_CACHE;

/**
 * Read-only aggregation service for the learner Portfolio & Progression view
 * (Phase 4). Combines {@link Engagement} lifecycle data with {@link Assessment}
 * competency scores — every figure is derived from real persisted records,
 * never mocked or estimated.
 */
@Service
public class PortfolioService {

    private static final Set<EngagementState> COMPLETED_STATES = Set.of(
            EngagementState.CLIENT_DECISION, EngagementState.REVIEW, EngagementState.COMPLETED);

    private final EngagementRepository engagementRepository;
    private final AssessmentRepository assessmentRepository;
    private final ScenarioRepository scenarioRepository;
    private final PersonaRepository personaRepository;

    public PortfolioService(EngagementRepository engagementRepository,
                             AssessmentRepository assessmentRepository,
                             ScenarioRepository scenarioRepository,
                             PersonaRepository personaRepository) {
        this.engagementRepository = engagementRepository;
        this.assessmentRepository = assessmentRepository;
        this.scenarioRepository = scenarioRepository;
        this.personaRepository = personaRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PORTFOLIO_SUMMARY_CACHE, key = "#userId")
    public PortfolioSummaryResponse getSummary(UUID userId) {
        List<Engagement> engagements = engagementRepository.findByUserId(userId);

        List<Engagement> completed = engagements.stream()
                .filter(e -> COMPLETED_STATES.contains(e.getState()))
                .toList();

        List<UUID> completedIds = completed.stream().map(Engagement::getId).toList();
        List<Assessment> assessments = completedIds.isEmpty()
                ? List.of()
                : assessmentRepository.findAllByEngagementIdIn(completedIds);

        Map<UUID, Assessment> assessmentByEngagement = assessments.stream()
                .collect(Collectors.toMap(Assessment::getEngagementId, a -> a));

        Map<UUID, Scenario> scenarioCache = new HashMap<>();

        int won = (int) assessments.stream()
                .filter(a -> "PROPOSAL_ACCEPTED".equals(a.getOutcome()))
                .count();
        int lost = (int) assessments.stream()
                .filter(a -> "PROPOSAL_REJECTED".equals(a.getOutcome()))
                .count();

        double avgScore = assessments.stream().mapToInt(Assessment::getOverallScore).average().orElse(0.0);

        List<CompletedEngagementView> history = completed.stream()
                .map(e -> {
                    Scenario scenario = scenarioCache.computeIfAbsent(e.getScenarioId(), this::loadScenario);
                    Assessment assessment = assessmentByEngagement.get(e.getId());
                    return new CompletedEngagementView(
                            e.getId(), e.getScenarioId(), scenario.getTitle(), scenario.getIndustry(),
                            assessment != null ? assessment.getOutcome() : e.getState().name(),
                            assessment != null ? assessment.getOverallScore() : 0,
                            e.getCompletedAt());
                })
                .sorted(Comparator.comparing(CompletedEngagementView::completedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<CompetencyTrend> trends = buildCompetencyTrends(assessmentByEngagement);

        return new PortfolioSummaryResponse(
                engagements.size(), completed.size(), won, lost, round1(avgScore), trends, history);
    }

    @Transactional(readOnly = true)
    public ReplayComparisonResponse compare(UUID userId, UUID engagementIdA, UUID engagementIdB) {
        return new ReplayComparisonResponse(
                snapshotOf(userId, engagementIdA),
                snapshotOf(userId, engagementIdB));
    }

    private EngagementSnapshot snapshotOf(UUID userId, UUID engagementId) {
        Engagement engagement = engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));
        Assessment assessment = assessmentRepository.findByEngagementId(engagementId)
                .orElseThrow(() -> new AssessmentNotReadyException(engagementId));
        Scenario scenario = loadScenario(engagement.getScenarioId());
        Persona persona = personaRepository.findById(engagement.getPersonaId())
                .orElseThrow(() -> new NotFoundException("Persona", engagement.getPersonaId()));

        List<CompetencyScoreView> scores = assessment.getCompetencyScores().stream()
                .map(this::toView)
                .toList();

        return new EngagementSnapshot(engagementId, scenario.getTitle(), persona.getName(),
                assessment.getOutcome(), assessment.getOverallScore(), scores);
    }

    private CompetencyScoreView toView(CompetencyScore score) {
        return new CompetencyScoreView(score.getCompetencyName(), score.getScore(), score.getEvidenceNote());
    }

    private List<CompetencyTrend> buildCompetencyTrends(Map<UUID, Assessment> assessmentByEngagement) {
        Map<String, List<TrendPoint>> byCompetency = new HashMap<>();
        assessmentByEngagement.values().forEach(assessment ->
                assessment.getCompetencyScores().forEach(score ->
                        byCompetency
                                .computeIfAbsent(score.getCompetencyName(), k -> new java.util.ArrayList<>())
                                .add(new TrendPoint(assessment.getEngagementId(), assessment.getGeneratedAt(), score.getScore()))));

        return byCompetency.entrySet().stream()
                .map(entry -> new CompetencyTrend(entry.getKey(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparing(TrendPoint::generatedAt))
                                .toList()))
                .sorted(Comparator.comparing(CompetencyTrend::competencyName))
                .toList();
    }

    private Scenario loadScenario(UUID scenarioId) {
        return scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new NotFoundException("Scenario", scenarioId));
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public static class AssessmentNotReadyException extends DomainException {
        public AssessmentNotReadyException(UUID engagementId) {
            super("Assessment not yet available for engagement: " + engagementId);
        }
    }
}
