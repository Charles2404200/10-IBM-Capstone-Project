package com.ibm.consulting.sim.engagement.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.engagement.domain.*;
import com.ibm.consulting.sim.scenario.application.LifecycleDefinitionCodec;
import com.ibm.consulting.sim.scenario.domain.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Supplier;

/** The application boundary for every engagement transition and lifecycle read model. */
@Service
public class EngagementLifecycleCoordinator {
    private static final Logger log = LoggerFactory.getLogger(EngagementLifecycleCoordinator.class);
    private final LifecycleDefinitionCodec codec;
    private final LifecycleFactRepository facts;
    private final MeterRegistry meters;
    private final LifecycleConditionEvaluator evaluator = new LifecycleConditionEvaluator();

    public EngagementLifecycleCoordinator(LifecycleDefinitionCodec codec, LifecycleFactRepository facts, MeterRegistry meters) {
        this.codec = Objects.requireNonNull(codec); this.facts = Objects.requireNonNull(facts); this.meters = Objects.requireNonNull(meters);
    }
    /** Compatibility for manually constructed legacy services. Custom conditions still fail closed on absent facts. */
    public static EngagementLifecycleCoordinator legacy() {
        return new EngagementLifecycleCoordinator(new LifecycleDefinitionCodec(new ObjectMapper()), ids -> Map.of(), new SimpleMeterRegistry());
    }
    public String snapshot(Scenario scenario) { return codec.encode(codec.decode(scenario.getLifecycleDefinition(), scenario.getObjective())); }
    public String retrySnapshot(Engagement original) {
        String snapshot = original.getLifecycleDefinitionSnapshot();
        if (snapshot == null) return codec.encode(ScenarioLifecycleDefinition.defaults());
        codec.decode(snapshot);
        return snapshot;
    }
    public boolean isLegacy(Engagement e) { return e.getLifecycleDefinitionSnapshot() == null; }

    public void selectLead(Engagement engagement, UUID leadId) {
        EngagementPolicy.assertValidTransition(engagement.getState(), EngagementState.CLIENT_INTELLIGENCE);
        engagement.assignLead(leadId);
        try { transition(engagement, EngagementState.CLIENT_INTELLIGENCE, "Lead selected: " + leadId); }
        catch (RuntimeException ex) { engagement.clearSelectedLeadAfterFailedSelection(); throw ex; }
    }
    public void transition(Engagement engagement, EngagementState target, String reason) {
        EngagementState from = engagement.getState();
        String outcome = "success";
        try {
            EngagementPolicy.assertValidTransition(from, target);
            // A failed meeting must always be able to terminate, even with a damaged snapshot.
            if (target != EngagementState.MEETING_FAILED) {
                var definition = codec.decode(engagement.getLifecycleDefinitionSnapshot());
                checkGates(engagement, target, definition, new LazyFacts(engagement));
            }
            engagement.transitionTo(target, reason);
        } catch (RuntimeException ex) {
            outcome = ex instanceof LifecycleGateNotSatisfiedException ? "blocked" : "invalid";
            throw ex;
        } finally {
            meters.counter("engagement.lifecycle.transitions", "from", from.name(), "to", target.name(), "outcome", outcome).increment();
            log.atInfo().addKeyValue("engagementId", engagement.getId()).addKeyValue("from", from)
                    .addKeyValue("to", target).addKeyValue("outcome", outcome).log("Engagement lifecycle transition");
        }
    }
    private void checkGates(Engagement e, EngagementState target, ScenarioLifecycleDefinition definition, Supplier<LifecycleFacts> context) {
        if (target == e.getState() || target == EngagementState.MEETING_FAILED) return;
        StageCapability from = LifecycleStageProgress.current(e.getState());
        StageCapability to = LifecycleStageProgress.current(target);
        boolean boundary = from != to;
        boolean milestone = target == EngagementState.HYPOTHESIS_READY || target == EngagementState.PROPOSAL_SUBMITTED;
        if (boundary || milestone) {
            ScenarioStageDefinition source = stage(definition, from);
            completeStage(source, definition, context, target);
        }
        if (boundary) {
            for (var entering : ordered(definition)) {
                if (entering.capability().ordinal() <= from.ordinal() || entering.capability().ordinal() > to.ordinal()) continue;
                if (entering.required()) require(entering.entryCondition(), context, false, "Enter " + entering.label());
                // The successful meeting path crosses MEETING_REVIEW without a dedicated technical state.
                if (entering.capability().ordinal() < to.ordinal()) completeStage(entering, definition, context, target);
            }
        }
        if (target == EngagementState.COMPLETED) {
            completeStage(stage(definition, StageCapability.COMPLETED), definition, context, target);
            for (var objective : definition.objectives()) {
                if (objective.required() && objective.stageKey() == null) requireObjective(objective, definition, context.get(), target);
            }
        }
    }
    private void completeStage(ScenarioStageDefinition stage, ScenarioLifecycleDefinition definition,
                               Supplier<LifecycleFacts> facts, EngagementState prospective) {
        if (stage.required()) require(stage.completionCondition(), facts, true, "Complete " + stage.label());
        for (var objective : definition.objectives()) {
            if (objective.required() && stage.key().equals(objective.stageKey())) requireObjective(objective, definition, facts.get(), prospective);
        }
    }
    private void require(LifecycleConditionNode condition, Supplier<LifecycleFacts> facts, boolean completed, String action) {
        if (condition == null) return;
        var result = evaluator.evaluate(condition, facts.get(), completed);
        if (!result.satisfied()) throw new LifecycleGateNotSatisfiedException(action + ": " + result.explanation());
    }
    private void requireObjective(ScenarioObjectiveDefinition objective, ScenarioLifecycleDefinition definition, LifecycleFacts facts, EngagementState state) {
        var result = objectiveResult(objective, definition, facts, state, new HashMap<>());
        if (!result.satisfied()) throw new LifecycleGateNotSatisfiedException("Objective " + objective.title() + ": " + result.explanation());
    }
    private LifecycleConditionEvaluator.Result objectiveResult(ScenarioObjectiveDefinition objective,
            ScenarioLifecycleDefinition definition, LifecycleFacts facts, EngagementState state,
            Map<String, LifecycleConditionEvaluator.Result> results) {
        if (results.containsKey(objective.key())) return results.get(objective.key());
        boolean technical = objective.stageKey() == null ? state == EngagementState.COMPLETED
                : LifecycleStageProgress.completed(definition.stages().stream().filter(s -> s.key().equals(objective.stageKey()))
                        .findFirst().orElseThrow().capability(), state, facts);
        var own = objective.completionCondition() == null
                ? new LifecycleConditionEvaluator.Result(technical, technical ? "Stage completed" : "Complete the associated stage")
                : evaluator.evaluate(objective.completionCondition(), facts, technical);
        var unmetChild = definition.objectives().stream().filter(child -> objective.key().equals(child.parentObjectiveKey()) && child.required())
                .map(child -> objectiveResult(child, definition, facts, state, results)).filter(result -> !result.satisfied()).findFirst();
        var result = own.satisfied() && unmetChild.isPresent() ? new LifecycleConditionEvaluator.Result(false, "Required subobjective: " + unmetChild.get().explanation()) : own;
        results.put(objective.key(), result);
        return result;
    }
    public EngagementResponse response(Engagement engagement) { return EngagementResponse.from(engagement).withLifecycle(resolve(engagement)); }
    public ResolvedEngagementLifecycle resolve(Engagement engagement) {
        return resolve(engagement, facts.loadAll(List.of(engagement.getId())).getOrDefault(engagement.getId(), LifecycleFacts.empty()));
    }
    public Map<UUID, LifecycleFacts> loadFacts(List<UUID> ids) { return ids.isEmpty() ? Map.of() : facts.loadAll(ids); }
    public ResolvedEngagementLifecycle resolve(Engagement e, LifecycleFacts storedFacts) {
        var definition = codec.decode(e.getLifecycleDefinitionSnapshot());
        var facts = storedFacts.withLeadSelected(e.getSelectedLeadId() != null);
        var current = LifecycleStageProgress.current(e.getState());
        var stages = ordered(definition);
        Set<StageCapability> available = EnumSet.noneOf(StageCapability.class);
        for (var target : EngagementState.values()) {
            if (target == EngagementState.MEETING_FAILED || !EngagementPolicy.canTransitionTo(e.getState(), target)) continue;
            try { checkGates(e, target, definition, () -> facts); available.add(LifecycleStageProgress.current(target)); }
            catch (LifecycleGateNotSatisfiedException ignored) { /* A locked stage is still a valid read model. */ }
        }
        if (facts.meetingCompleted()) available.add(StageCapability.MEETING_REVIEW);
        var resolvedStages = stages.stream().map(s -> new ResolvedEngagementLifecycle.Stage(s.key(),s.capability(),s.label(),s.description(),
                s.goal(),s.doneText(),s.nextText(),s.required(), s.capability() == current ? "CURRENT"
                : LifecycleStageProgress.completed(s.capability(),e.getState(),facts) ? "COMPLETED"
                : available.contains(s.capability()) ? "AVAILABLE" : "LOCKED")).toList();
        var results = new HashMap<String,LifecycleConditionEvaluator.Result>();
        var objectives = definition.objectives().stream().sorted(Comparator.comparingInt(ScenarioObjectiveDefinition::displayOrder)).map(o -> {
            var result = objectiveResult(o,definition,facts,e.getState(),results);
            return new ResolvedEngagementLifecycle.Objective(o.key(),o.parentObjectiveKey(),o.stageKey(),o.title(),o.description(),o.required(),result.satisfied(),result.explanation());
        }).toList();
        int index = java.util.stream.IntStream.range(0, stages.size()).filter(i -> stages.get(i).capability() == current).findFirst().orElseThrow();
        return new ResolvedEngagementLifecycle(new ResolvedEngagementLifecycle.Lifecycle(resolvedStages,stages.get(index).key(),index,
                stages.size(),EngagementProgressCalculator.progressPercent(e.getState())),objectives);
    }
    private List<ScenarioStageDefinition> ordered(ScenarioLifecycleDefinition definition) {
        return definition.stages().stream().sorted(Comparator.comparingInt(ScenarioStageDefinition::displayOrder)).toList();
    }
    private ScenarioStageDefinition stage(ScenarioLifecycleDefinition definition, StageCapability capability) {
        return definition.stages().stream().filter(s -> s.capability() == capability).findFirst().orElseThrow();
    }
    private class LazyFacts implements Supplier<LifecycleFacts> {
        private final Engagement engagement;
        private LifecycleFacts value;
        LazyFacts(Engagement engagement) { this.engagement = engagement; }
        public LifecycleFacts get() {
            if (value == null) value = facts.loadAll(List.of(engagement.getId())).getOrDefault(engagement.getId(), LifecycleFacts.empty())
                    .withLeadSelected(engagement.getSelectedLeadId() != null);
            return value;
        }
    }
}
