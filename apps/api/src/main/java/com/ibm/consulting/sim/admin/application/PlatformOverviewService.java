package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.assessment.domain.Assessment;
import com.ibm.consulting.sim.assessment.domain.AssessmentRepository;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PlatformOverviewService {
    private final EngagementRepository engagementRepository;
    private final AssessmentRepository assessmentRepository;
    private final ScenarioRepository scenarioRepository;

    public PlatformOverviewService(EngagementRepository engagementRepository, AssessmentRepository assessmentRepository,
                                   ScenarioRepository scenarioRepository) {
        this.engagementRepository = engagementRepository;
        this.assessmentRepository = assessmentRepository;
        this.scenarioRepository = scenarioRepository;
    }

    @Transactional(readOnly = true)
    public PlatformOverviewResponse getOverview() {
        List<Engagement> engagements = engagementRepository.findAll();
        Map<UUID, Assessment> assessments = assessmentRepository.findAllByEngagementIdIn(
                        engagements.stream().map(Engagement::getId).toList()).stream()
                .collect(Collectors.toMap(Assessment::getEngagementId, Function.identity()));
        Map<UUID, Scenario> scenarios = scenarioRepository.findAll().stream()
                .collect(Collectors.toMap(Scenario::getId, Function.identity()));
        long completed = engagements.stream().filter(engagement -> engagement.getCompletedAt() != null).count();
        long active = engagements.size() - completed;
        Integer averageScore = averageScore(assessments.values().stream().toList());
        Map<String, Long> byState = engagements.stream().collect(Collectors.groupingBy(
                engagement -> engagement.getState().name(), Collectors.counting()));
        List<PlatformOverviewResponse.ScenarioActivity> activity = engagements.stream()
                .collect(Collectors.groupingBy(Engagement::getScenarioId)).entrySet().stream()
                .map(entry -> scenarioActivity(entry.getKey(), entry.getValue(), assessments, scenarios))
                .sorted(java.util.Comparator.comparingLong(PlatformOverviewResponse.ScenarioActivity::engagementCount).reversed())
                .toList();
        return new PlatformOverviewResponse(engagements.size(), active, completed,
                engagements.isEmpty() ? 0 : Math.round(completed * 100f / engagements.size()), averageScore,
                Map.copyOf(byState), activity);
    }

    private PlatformOverviewResponse.ScenarioActivity scenarioActivity(UUID scenarioId, List<Engagement> engagements,
                                                                         Map<UUID, Assessment> assessments,
                                                                         Map<UUID, Scenario> scenarios) {
        List<Assessment> scores = engagements.stream().map(engagement -> assessments.get(engagement.getId()))
                .filter(java.util.Objects::nonNull).toList();
        long completed = engagements.stream().filter(engagement -> engagement.getCompletedAt() != null).count();
        Scenario scenario = scenarios.get(scenarioId);
        return new PlatformOverviewResponse.ScenarioActivity(scenarioId.toString(),
                scenario == null ? "Retired scenario" : scenario.getTitle(), engagements.size(), completed, averageScore(scores));
    }

    private Integer averageScore(List<Assessment> assessments) {
        return assessments.isEmpty() ? null : Math.round((float) assessments.stream()
                .mapToInt(Assessment::getOverallScore).average().orElse(0));
    }
}
