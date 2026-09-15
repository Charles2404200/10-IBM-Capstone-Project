package com.ibm.consulting.sim.engagement.domain;

import com.ibm.consulting.sim.scenario.domain.LifecycleConditionNode;

/** Pure, deterministic evaluation. Author text is never executable or sent to an LLM. */
public final class LifecycleConditionEvaluator {
    public record Result(boolean satisfied, String explanation) {}

    public Result evaluate(LifecycleConditionNode condition, LifecycleFacts facts, boolean stageCompleted) {
        if (condition == null) return new Result(true, "No additional condition");
        if (condition.kind() == LifecycleConditionNode.Kind.GROUP) {
            var results = condition.children().stream().map(c -> evaluate(c, facts, stageCompleted)).toList();
            boolean passed = condition.operator() == LifecycleConditionNode.Operator.AND
                    ? results.stream().allMatch(Result::satisfied) : results.stream().anyMatch(Result::satisfied);
            return new Result(passed, passed ? "Conditions met" : results.stream().filter(r -> !r.satisfied())
                    .map(Result::explanation).collect(java.util.stream.Collectors.joining("; ")));
        }
        boolean passed = switch (condition.conditionType()) {
            case LEAD_SELECTED -> facts.leadSelected();
            case MIN_EVIDENCE_COUNT -> facts.evidenceCount() >= condition.threshold();
            case HAS_HYPOTHESIS -> facts.hasHypothesis();
            case MIN_RESEARCH_CONFIDENCE -> meets(facts.researchConfidence(), condition.threshold());
            case OUTREACH_ACCEPTED -> facts.outreachAccepted();
            case PREPARATION_READY -> facts.preparationReady();
            case MIN_PREPARATION_SCORE -> meets(facts.preparationScore(), condition.threshold());
            case MEETING_STARTED -> facts.meetingStarted();
            case MEETING_COMPLETED -> facts.meetingCompleted();
            case MIN_TRUST -> meets(facts.trust(), condition.threshold());
            case MIN_INTEREST -> meets(facts.interest(), condition.threshold());
            case MIN_PATIENCE -> meets(facts.patience(), condition.threshold());
            case PROPOSAL_CREATED -> facts.proposalCreated();
            case PROPOSAL_SUBMITTED -> facts.proposalSubmitted();
            case CLIENT_DECISION_AVAILABLE -> facts.clientDecisionAvailable();
            case ASSESSMENT_AVAILABLE -> facts.assessmentAvailable();
            case CURRENT_STAGE_COMPLETED -> stageCompleted;
        };
        String label = condition.conditionType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return new Result(passed, passed ? "Completed: " + label : "Required: " + label
                + (condition.threshold() == null ? "" : " >= " + condition.threshold()));
    }
    private boolean meets(Integer actual, Double threshold) { return actual != null && actual >= threshold; }
}
