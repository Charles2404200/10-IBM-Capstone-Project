package com.ibm.consulting.sim.scenario.domain;

import java.util.List;

/** Versioned, immutable lifecycle presentation and objective contract owned by a scenario revision. */
public record ScenarioLifecycleDefinition(
        int schemaVersion,
        List<ScenarioStageDefinition> stages,
        List<ScenarioObjectiveDefinition> objectives) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ScenarioLifecycleDefinition {
        stages = stages == null ? List.of() : List.copyOf(stages);
        objectives = objectives == null ? List.of() : List.copyOf(objectives);
    }

    /** Legacy-safe lifecycle with the existing ten labels and guidance and no authored gates. */
    public static ScenarioLifecycleDefinition defaults() {
        return defaults(null);
    }

    /** Maps a legacy briefing objective to one terminal objective; blank legacy content remains absent. */
    public static ScenarioLifecycleDefinition defaults(String legacyObjective) {
        List<ScenarioObjectiveDefinition> objectives = legacyObjective == null || legacyObjective.isBlank()
                ? List.of()
                : List.of(new ScenarioObjectiveDefinition(
                        "MAIN_OBJECTIVE", null, "COMPLETED", legacyObjective.trim(), "", true, 0,
                        LifecycleConditionNode.leaf(LifecycleConditionType.CURRENT_STAGE_COMPLETED)));
        return new ScenarioLifecycleDefinition(CURRENT_SCHEMA_VERSION, defaultStages(), objectives);
    }

    private static List<ScenarioStageDefinition> defaultStages() {
        return List.of(
                stage(StageCapability.LEAD, "Choose a client",
                        "Choose which client opportunity to pursue, on deliberately thin information.",
                        "You have committed to one lead.",
                        "The research library opens so you can find out who you just committed to.", true),
                stage(StageCapability.CLIENT_INTELLIGENCE, "Research the client",
                        "Gather evidence about the client and commit to a hypothesis about their real problem.",
                        "Enough corroborated evidence, a named stakeholder, and a submitted hypothesis.",
                        "The outreach desk opens so you can contact them with something to say.", true),
                stage(StageCapability.OUTREACH, "Make contact",
                        "Earn a meeting by email. One clear reason, one low-friction ask.",
                        "The client agrees to meet.",
                        "The prep room opens so you can decide what the meeting is for.", true),
                stage(StageCapability.MEETING_PREPARATION, "Prepare",
                        "Set an objective, an agenda, and the questions that will actually reveal something.",
                        "Your prep clears the readiness threshold.",
                        "The meeting room opens. The client is waiting.", true),
                stage(StageCapability.LIVE_MEETING, "The meeting",
                        "Run the conversation. Build trust, hold their interest, do not burn their patience.",
                        "You leave with the discovery you needed and the relationship intact.",
                        "The debrief nook opens so you can see what that conversation cost and earned.", true),
                stage(StageCapability.MEETING_REVIEW, "Debrief",
                        "Look back at the conversation: what you uncovered, what you left on the table.",
                        "You have read the debrief.",
                        "The proposal studio opens.", false),
                stage(StageCapability.PROPOSAL, "Proposal",
                        "Turn evidence into a recommendation with a budget, a timeline and named risks.",
                        "A submitted proposal grounded in evidence you actually collected.",
                        "The client decides.", true),
                stage(StageCapability.OUTCOME, "Their decision",
                        "Receive the client decision. Research, relationship and proposal all count here.",
                        "The client has responded.",
                        "Your performance review is generated.", true),
                stage(StageCapability.REVIEW, "Your review",
                        "Read a structured assessment of how you performed and what to work on.",
                        "You have reviewed your competency scores.",
                        "The engagement joins your portfolio.", true),
                stage(StageCapability.COMPLETED, "Portfolio",
                        "This engagement is closed and recorded.",
                        "Nothing further — start another scenario to keep building the portfolio.",
                        "Harder scenarios are worth attempting once this one is behind you.", true));
    }

    private static ScenarioStageDefinition stage(
            StageCapability capability, String label, String goal, String done, String next, boolean required) {
        return new ScenarioStageDefinition(capability.name(), capability, label, "", goal, done, next, required,
                capability.ordinal(), null, null);
    }
}
