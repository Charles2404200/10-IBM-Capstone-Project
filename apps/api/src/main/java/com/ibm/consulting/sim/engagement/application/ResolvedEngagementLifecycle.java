package com.ibm.consulting.sim.engagement.application;

import com.ibm.consulting.sim.scenario.domain.StageCapability;
import java.util.List;

public record ResolvedEngagementLifecycle(Lifecycle lifecycle, List<Objective> objectives) {
    public record Lifecycle(List<Stage> stages, String currentStageKey, int currentStageIndex,
                            int totalStages, int progressPercent) {}
    public record Stage(String key, StageCapability capability, String label, String description, String goal,
                        String doneText, String nextText, boolean required, String status) {}
    public record Objective(String key, String parentObjectiveKey, String stageKey, String title,
                            String description, boolean required, boolean completed, String completionExplanation) {}
}
