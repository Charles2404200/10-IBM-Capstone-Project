package com.ibm.consulting.sim.scenario.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioLifecycleDefinitionValidatorTest {

    private final ScenarioLifecycleDefinitionValidator validator = new ScenarioLifecycleDefinitionValidator();

    @Test
    void defaultsPreserveTheExistingLifecycleOrderAndLearnerGuidance() {
        ScenarioLifecycleDefinition definition = ScenarioLifecycleDefinition.defaults();

        assertThat(definition.schemaVersion()).isEqualTo(1);
        assertThat(definition.stages()).extracting(ScenarioStageDefinition::capability)
                .containsExactly(StageCapability.values());
        assertThat(definition.stages()).extracting(ScenarioStageDefinition::label)
                .containsExactly("Choose a client", "Research the client", "Make contact", "Prepare",
                        "The meeting", "Debrief", "Proposal", "Their decision", "Your review", "Portfolio");
        assertThat(definition.stages()).extracting(ScenarioStageDefinition::goal)
                .containsExactly(
                        "Choose which client opportunity to pursue, on deliberately thin information.",
                        "Gather evidence about the client and commit to a hypothesis about their real problem.",
                        "Earn a meeting by email. One clear reason, one low-friction ask.",
                        "Set an objective, an agenda, and the questions that will actually reveal something.",
                        "Run the conversation. Build trust, hold their interest, do not burn their patience.",
                        "Look back at the conversation: what you uncovered, what you left on the table.",
                        "Turn evidence into a recommendation with a budget, a timeline and named risks.",
                        "Receive the client decision. Research, relationship and proposal all count here.",
                        "Read a structured assessment of how you performed and what to work on.",
                        "This engagement is closed and recorded.");
        assertThat(definition.stages()).extracting(ScenarioStageDefinition::doneText)
                .containsExactly(
                        "You have committed to one lead.",
                        "Enough corroborated evidence, a named stakeholder, and a submitted hypothesis.",
                        "The client agrees to meet.",
                        "Your prep clears the readiness threshold.",
                        "You leave with the discovery you needed and the relationship intact.",
                        "You have read the debrief.",
                        "A submitted proposal grounded in evidence you actually collected.",
                        "The client has responded.",
                        "You have reviewed your competency scores.",
                        "Nothing further — start another scenario to keep building the portfolio.");
        assertThat(definition.stages()).extracting(ScenarioStageDefinition::nextText)
                .containsExactly(
                        "The research library opens so you can find out who you just committed to.",
                        "The outreach desk opens so you can contact them with something to say.",
                        "The prep room opens so you can decide what the meeting is for.",
                        "The meeting room opens. The client is waiting.",
                        "The debrief nook opens so you can see what that conversation cost and earned.",
                        "The proposal studio opens.",
                        "The client decides.",
                        "Your performance review is generated.",
                        "The engagement joins your portfolio.",
                        "Harder scenarios are worth attempting once this one is behind you.");
        assertThat(definition.stages()).allMatch(stage -> stage.entryCondition() == null
                && stage.completionCondition() == null);
        assertThat(definition.objectives()).isEmpty();
        validator.validate(definition);
    }

    @Test
    void legacyObjectiveBecomesARequiredTerminalObjective() {
        ScenarioLifecycleDefinition definition = ScenarioLifecycleDefinition.defaults("  Grow trusted client relationships  ");

        assertThat(definition.objectives()).singleElement().satisfies(objective -> {
            assertThat(objective.key()).isEqualTo("MAIN_OBJECTIVE");
            assertThat(objective.parentObjectiveKey()).isNull();
            assertThat(objective.stageKey()).isEqualTo("COMPLETED");
            assertThat(objective.title()).isEqualTo("Grow trusted client relationships");
            assertThat(objective.required()).isTrue();
            assertThat(objective.completionCondition()).isEqualTo(
                    LifecycleConditionNode.leaf(LifecycleConditionType.CURRENT_STAGE_COMPLETED));
        });
        assertThat(ScenarioLifecycleDefinition.defaults(" \t ").objectives()).isEmpty();
        assertThat(ScenarioLifecycleDefinition.defaults(null).objectives()).isEmpty();
        validator.validate(definition);
    }

    @Test
    void rejectsDuplicateStageIdentityAndOrdering() {
        ScenarioLifecycleDefinition baseline = ScenarioLifecycleDefinition.defaults();
        List<ScenarioStageDefinition> stages = new ArrayList<>(baseline.stages());
        ScenarioStageDefinition second = stages.get(1);
        stages.set(1, copyStage(second, stages.get(0).key(), stages.get(0).capability(), stages.get(0).displayOrder()));

        assertThat(validator.errors(new ScenarioLifecycleDefinition(1, stages, List.of())))
                .anyMatch(error -> error.contains("stage key"))
                .anyMatch(error -> error.contains("capability"))
                .anyMatch(error -> error.contains("display order"));
    }

    @Test
    void rejectsMissingWronglyOrderedOrNonTerminalCapabilities() {
        ScenarioLifecycleDefinition baseline = ScenarioLifecycleDefinition.defaults();
        List<ScenarioStageDefinition> missingLead = baseline.stages().subList(1, baseline.stages().size());
        assertThat(validator.errors(new ScenarioLifecycleDefinition(1, missingLead, List.of())))
                .anyMatch(error -> error.contains("LEAD"));

        List<ScenarioStageDefinition> reordered = new ArrayList<>(baseline.stages());
        ScenarioStageDefinition research = reordered.get(1);
        ScenarioStageDefinition outreach = reordered.get(2);
        reordered.set(1, copyStage(research, research.key(), research.capability(), outreach.displayOrder()));
        reordered.set(2, copyStage(outreach, outreach.key(), outreach.capability(), research.displayOrder()));
        assertThat(validator.errors(new ScenarioLifecycleDefinition(1, reordered, List.of())))
                .anyMatch(error -> error.contains("dependency order"));

        List<ScenarioStageDefinition> completedFirst = new ArrayList<>(baseline.stages());
        ScenarioStageDefinition lead = completedFirst.get(0);
        ScenarioStageDefinition completed = completedFirst.get(9);
        completedFirst.set(0, copyStage(lead, lead.key(), lead.capability(), completed.displayOrder()));
        completedFirst.set(9, copyStage(completed, completed.key(), completed.capability(), lead.displayOrder()));
        assertThatThrownBy(() -> validator.validate(new ScenarioLifecycleDefinition(1, completedFirst, List.of())))
                .isInstanceOf(InvalidScenarioLifecycleDefinitionException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void onlyMeetingReviewMayBeOptionalAndEveryCapabilityMustRemainPresent() {
        ScenarioLifecycleDefinition baseline = ScenarioLifecycleDefinition.defaults();
        assertThat(baseline.stages()).filteredOn(stage -> !stage.required())
                .extracting(ScenarioStageDefinition::capability)
                .containsExactly(StageCapability.MEETING_REVIEW);

        List<ScenarioStageDefinition> stages = new ArrayList<>(baseline.stages());
        ScenarioStageDefinition proposal = stages.get(6);
        stages.set(6, copyStage(proposal, proposal.key(), proposal.capability(), proposal.displayOrder(), false));
        assertThat(validator.errors(new ScenarioLifecycleDefinition(1, stages, List.of())))
                .anyMatch(error -> error.contains("MEETING_REVIEW"));
    }

    @Test
    void validatesKeysTextBoundsAndCollectionLimits() {
        ScenarioLifecycleDefinition baseline = ScenarioLifecycleDefinition.defaults();
        List<ScenarioStageDefinition> stages = new ArrayList<>(baseline.stages());
        ScenarioStageDefinition lead = stages.get(0);
        stages.set(0, new ScenarioStageDefinition("not-valid", lead.capability(), " ", lead.description(),
                lead.goal(), lead.doneText(), lead.nextText(), lead.required(), -1, null, null));

        List<ScenarioObjectiveDefinition> objectives = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            objectives.add(new ScenarioObjectiveDefinition("OBJECTIVE_" + i, null, "LEAD", "Objective " + i,
                    "", false, i, null));
        }

        assertThat(validator.errors(new ScenarioLifecycleDefinition(1, stages, objectives)))
                .anyMatch(error -> error.contains("stage key"))
                .anyMatch(error -> error.contains("label"))
                .anyMatch(error -> error.contains("nonnegative"))
                .anyMatch(error -> error.contains("50 objectives"));
    }

    @Test
    void rejectsUnknownObjectiveReferencesDuplicateIdentityAndParentCycles() {
        List<ScenarioObjectiveDefinition> objectives = List.of(
                objective("A", "B", "LEAD", 0),
                objective("B", "A", "MISSING_STAGE", 0),
                objective("A", "UNKNOWN_PARENT", "LEAD", 1));

        assertThat(validator.errors(new ScenarioLifecycleDefinition(1,
                ScenarioLifecycleDefinition.defaults().stages(), objectives)))
                .anyMatch(error -> error.contains("objective key"))
                .anyMatch(error -> error.contains("objective display order"))
                .anyMatch(error -> error.contains("unknown stage"))
                .anyMatch(error -> error.contains("unknown parent"))
                .anyMatch(error -> error.contains("cycle"));
    }

    @Test
    void allowsGlobalObjectivesAndRejectsRequiredChildrenScheduledAfterTheirParent() {
        ScenarioLifecycleDefinition baseline = ScenarioLifecycleDefinition.defaults();
        ScenarioObjectiveDefinition global = new ScenarioObjectiveDefinition(
                "GLOBAL", null, null, "Finish the engagement", "", true, 0,
                LifecycleConditionNode.leaf(LifecycleConditionType.ASSESSMENT_AVAILABLE));
        validator.validate(new ScenarioLifecycleDefinition(1, baseline.stages(), List.of(global)));

        List<ScenarioObjectiveDefinition> invalidTree = List.of(
                objective("PARENT", null, "CLIENT_INTELLIGENCE", 0),
                objective("CHILD", "PARENT", "PROPOSAL", 1));
        assertThat(validator.errors(new ScenarioLifecycleDefinition(1, baseline.stages(), invalidTree)))
                .anyMatch(error -> error.contains("required child") && error.contains("after parent"));
    }

    @Test
    void rejectsMalformedOversizedAndTooDeepConditionTrees() {
        LifecycleConditionNode malformedGroup = new LifecycleConditionNode(
                LifecycleConditionNode.Kind.GROUP, null, List.of(), LifecycleConditionType.LEAD_SELECTED, 1.0, "x");
        LifecycleConditionNode tooManyChildren = LifecycleConditionNode.group(
                LifecycleConditionNode.Operator.AND,
                java.util.Collections.nCopies(11, LifecycleConditionNode.leaf(LifecycleConditionType.HAS_HYPOTHESIS)));
        LifecycleConditionNode tooDeep = LifecycleConditionNode.leaf(LifecycleConditionType.LEAD_SELECTED);
        for (int i = 0; i < 4; i++) {
            tooDeep = LifecycleConditionNode.group(LifecycleConditionNode.Operator.AND, List.of(tooDeep));
        }

        assertThat(conditionErrors(malformedGroup)).anyMatch(error -> error.contains("GROUP"));
        assertThat(conditionErrors(tooManyChildren)).anyMatch(error -> error.contains("10 children"));
        assertThat(conditionErrors(tooDeep)).anyMatch(error -> error.contains("depth 4"));

        LifecycleConditionNode shared = LifecycleConditionNode.leaf(LifecycleConditionType.LEAD_SELECTED);
        List<ScenarioStageDefinition> stages = new ArrayList<>(ScenarioLifecycleDefinition.defaults().stages());
        for (int i = 0; i < stages.size(); i++) {
            ScenarioStageDefinition stage = stages.get(i);
            LifecycleConditionNode large = LifecycleConditionNode.group(LifecycleConditionNode.Operator.OR,
                    java.util.Collections.nCopies(10, shared));
            stages.set(i, withCompletion(stage, large));
        }
        assertThat(validator.errors(new ScenarioLifecycleDefinition(1, stages,
                List.of(objective("EXTRA", null, "COMPLETED", 0, shared)))))
                .anyMatch(error -> error.contains("100 condition nodes"));
    }

    @Test
    void rejectsInvalidThresholdAndValueShapesByConditionType() {
        assertThat(conditionErrors(LifecycleConditionNode.leaf(LifecycleConditionType.MIN_EVIDENCE_COUNT, 2.5)))
                .anyMatch(error -> error.contains("integer"));
        assertThat(conditionErrors(LifecycleConditionNode.leaf(LifecycleConditionType.MIN_EVIDENCE_COUNT, 1001)))
                .anyMatch(error -> error.contains("0 and 1000"));
        assertThat(conditionErrors(LifecycleConditionNode.leaf(LifecycleConditionType.MIN_TRUST, 101)))
                .anyMatch(error -> error.contains("0 and 100"));
        assertThat(conditionErrors(new LifecycleConditionNode(LifecycleConditionNode.Kind.LEAF, null, null,
                LifecycleConditionType.HAS_HYPOTHESIS, 1.0, null)))
                .anyMatch(error -> error.contains("threshold"));
        assertThat(conditionErrors(new LifecycleConditionNode(LifecycleConditionNode.Kind.LEAF, null, null,
                LifecycleConditionType.LEAD_SELECTED, null, "script:evil")))
                .anyMatch(error -> error.contains("value"));
        assertThat(conditionErrors(new LifecycleConditionNode(LifecycleConditionNode.Kind.LEAF, null, null,
                null, null, null))).anyMatch(error -> error.contains("condition type"));
    }

    @Test
    void rejectsConditionsBeforeTheirDataExistsAndStageCompletionRecursion() {
        List<ScenarioStageDefinition> stages = new ArrayList<>(ScenarioLifecycleDefinition.defaults().stages());
        stages.set(0, withCompletion(stages.get(0), LifecycleConditionNode.leaf(LifecycleConditionType.PROPOSAL_SUBMITTED)));
        stages.set(1, withEntry(stages.get(1), LifecycleConditionNode.leaf(LifecycleConditionType.HAS_HYPOTHESIS)));
        stages.set(2, withCompletion(stages.get(2), LifecycleConditionNode.leaf(LifecycleConditionType.CURRENT_STAGE_COMPLETED)));

        assertThat(validator.errors(new ScenarioLifecycleDefinition(1, stages, List.of())))
                .anyMatch(error -> error.contains("not available") && error.contains("LEAD"))
                .anyMatch(error -> error.contains("entry") && error.contains("CLIENT_INTELLIGENCE"))
                .anyMatch(error -> error.contains("CURRENT_STAGE_COMPLETED"));
    }

    @Test
    void currentStageCompletedIsValidForObjectives() {
        ScenarioLifecycleDefinition baseline = ScenarioLifecycleDefinition.defaults();
        ScenarioObjectiveDefinition objective = objective("READ_DEBRIEF", null, "MEETING_REVIEW", 0,
                LifecycleConditionNode.leaf(LifecycleConditionType.CURRENT_STAGE_COMPLETED));

        validator.validate(new ScenarioLifecycleDefinition(1, baseline.stages(), List.of(objective)));
        assertThat(LifecycleConditionType.MIN_TRUST.availableFrom()).contains(StageCapability.LIVE_MEETING);
        assertThat(LifecycleConditionType.CURRENT_STAGE_COMPLETED.availableFrom()).isEmpty();
    }

    @Test
    void allowsSameStageEntryFactsThatExistBeforeTheTechnicalTransition() {
        List<ScenarioStageDefinition> stages = new ArrayList<>(ScenarioLifecycleDefinition.defaults().stages());
        stages.set(4, withEntry(stages.get(4), LifecycleConditionNode.group(
                LifecycleConditionNode.Operator.AND,
                List.of(LifecycleConditionNode.leaf(LifecycleConditionType.MEETING_STARTED),
                        LifecycleConditionNode.leaf(LifecycleConditionType.MIN_TRUST, 40)))));
        stages.set(7, withEntry(stages.get(7),
                LifecycleConditionNode.leaf(LifecycleConditionType.CLIENT_DECISION_AVAILABLE)));
        stages.set(8, withEntry(stages.get(8),
                LifecycleConditionNode.leaf(LifecycleConditionType.ASSESSMENT_AVAILABLE)));

        validator.validate(new ScenarioLifecycleDefinition(1, stages, List.of()));
    }

    @Test
    void rejectsUnsupportedSchemaAndNullDefinition() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(InvalidScenarioLifecycleDefinitionException.class);
        assertThatThrownBy(() -> validator.validate(new ScenarioLifecycleDefinition(2,
                ScenarioLifecycleDefinition.defaults().stages(), List.of())))
                .isInstanceOf(InvalidScenarioLifecycleDefinitionException.class)
                .hasMessageContaining("schema version");
    }

    private List<String> conditionErrors(LifecycleConditionNode condition) {
        List<ScenarioStageDefinition> stages = new ArrayList<>(ScenarioLifecycleDefinition.defaults().stages());
        stages.set(9, withCompletion(stages.get(9), condition));
        return validator.errors(new ScenarioLifecycleDefinition(1, stages, List.of()));
    }

    private static ScenarioObjectiveDefinition objective(String key, String parent, String stage, int order) {
        return objective(key, parent, stage, order, null);
    }

    private static ScenarioObjectiveDefinition objective(
            String key, String parent, String stage, int order, LifecycleConditionNode condition) {
        return new ScenarioObjectiveDefinition(key, parent, stage, key + " title", "", true, order, condition);
    }

    private static ScenarioStageDefinition withEntry(ScenarioStageDefinition stage, LifecycleConditionNode condition) {
        return new ScenarioStageDefinition(stage.key(), stage.capability(), stage.label(), stage.description(),
                stage.goal(), stage.doneText(), stage.nextText(), stage.required(), stage.displayOrder(), condition,
                stage.completionCondition());
    }

    private static ScenarioStageDefinition withCompletion(ScenarioStageDefinition stage, LifecycleConditionNode condition) {
        return new ScenarioStageDefinition(stage.key(), stage.capability(), stage.label(), stage.description(),
                stage.goal(), stage.doneText(), stage.nextText(), stage.required(), stage.displayOrder(),
                stage.entryCondition(), condition);
    }

    private static ScenarioStageDefinition copyStage(
            ScenarioStageDefinition stage, String key, StageCapability capability, int order) {
        return copyStage(stage, key, capability, order, stage.required());
    }

    private static ScenarioStageDefinition copyStage(
            ScenarioStageDefinition stage, String key, StageCapability capability, int order, boolean required) {
        return new ScenarioStageDefinition(key, capability, stage.label(), stage.description(), stage.goal(),
                stage.doneText(), stage.nextText(), required, order, stage.entryCondition(), stage.completionCondition());
    }
}
