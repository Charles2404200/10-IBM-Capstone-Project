package com.ibm.consulting.sim.portfolio.application;

import com.ibm.consulting.sim.assessment.domain.AssessmentRepository;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.scenario.domain.PersonaRepository;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioServiceTest {

    @Test
    void onlyTerminallyCompletedEngagementsAppearInHistoryAndMetrics() {
        UUID userId = UUID.randomUUID();
        UUID scenarioId = UUID.randomUUID();
        Engagement clientDecision = engagementInState(scenarioId, EngagementState.CLIENT_DECISION);
        Engagement review = engagementInState(scenarioId, EngagementState.REVIEW);
        Engagement completed = engagementInState(scenarioId, EngagementState.COMPLETED);
        EngagementRepository engagements = mock(EngagementRepository.class);
        AssessmentRepository assessments = mock(AssessmentRepository.class);
        ScenarioRepository scenarios = mock(ScenarioRepository.class);
        when(engagements.findByUserId(userId)).thenReturn(List.of(clientDecision, review, completed));
        when(assessments.findAllByEngagementIdIn(List.of(completed.getId()))).thenReturn(List.of());
        when(scenarios.findById(scenarioId))
                .thenReturn(Optional.of(Scenario.create("Scenario", "Retail", "Description", 3)));

        PortfolioSummaryResponse summary = new PortfolioService(engagements, assessments, scenarios,
                mock(PersonaRepository.class)).getSummary(userId);

        assertThat(summary.totalEngagements()).isEqualTo(3);
        assertThat(summary.completedEngagements()).isEqualTo(1);
        assertThat(summary.completedEngagementsHistory())
                .extracting(PortfolioSummaryResponse.CompletedEngagementView::engagementId)
                .containsExactly(completed.getId());
    }

    private Engagement engagementInState(UUID scenarioId, EngagementState target) {
        Engagement engagement = mock(Engagement.class);
        when(engagement.getId()).thenReturn(UUID.randomUUID());
        when(engagement.getScenarioId()).thenReturn(scenarioId);
        when(engagement.getState()).thenReturn(target);
        return engagement;
    }
}
