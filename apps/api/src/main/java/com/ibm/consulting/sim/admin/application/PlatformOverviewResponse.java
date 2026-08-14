package com.ibm.consulting.sim.admin.application;

import java.util.List;
import java.util.Map;

/** Read-only, aggregated platform telemetry for authorised administrators. */
public record PlatformOverviewResponse(
        long totalEngagements,
        long activeEngagements,
        long completedEngagements,
        int completionRatePercent,
        Integer averageAssessmentScore,
        Map<String, Long> engagementsByState,
        Map<String, Long> scenariosByStatus,
        List<ScenarioActivity> scenarios) {

    public record ScenarioActivity(String scenarioId, String title, long engagementCount, long completedCount,
                                   Integer averageAssessmentScore) {}
}
