package com.ibm.consulting.sim.engagement.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.engagement.domain.*;
import com.ibm.consulting.sim.scenario.application.LifecycleDefinitionCodec;
import com.ibm.consulting.sim.scenario.domain.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.ibm.consulting.sim.scenario.domain.LifecycleConditionType.*;

class LifecycleRuntimeTest {
    private final LifecycleDefinitionCodec codec = new LifecycleDefinitionCodec(new ObjectMapper());
    private final LifecycleConditionEvaluator evaluator = new LifecycleConditionEvaluator();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final EngagementLifecycleCoordinator coordinator = new EngagementLifecycleCoordinator(codec, ids -> Map.of(), meters);

    @Test void missingFactsDoNotSatisfyZeroThresholdScores() {
        for (var type : List.of(MIN_TRUST, MIN_INTEREST, MIN_PATIENCE, MIN_PREPARATION_SCORE, MIN_RESEARCH_CONFIDENCE)) {
            assertThat(evaluator.evaluate(LifecycleConditionNode.leaf(type, 0), LifecycleFacts.empty(), false).satisfied()).isFalse();
        }
    }
    @Test void groupedConditionsAreDeterministic() {
        var node = LifecycleConditionNode.group(LifecycleConditionNode.Operator.OR, List.of(
                LifecycleConditionNode.leaf(LEAD_SELECTED), LifecycleConditionNode.leaf(MIN_EVIDENCE_COUNT, 0)));
        assertThat(evaluator.evaluate(node, LifecycleFacts.empty(), false).satisfied()).isTrue();
        assertThat(evaluator.evaluate(LifecycleConditionNode.leaf(CURRENT_STAGE_COMPLETED), LifecycleFacts.empty(), false).satisfied()).isFalse();
        assertThat(evaluator.evaluate(LifecycleConditionNode.leaf(CURRENT_STAGE_COMPLETED), LifecycleFacts.empty(), true).satisfied()).isTrue();
    }
    @Test void policyIsCheckedBeforeParsingAuthoredData() {
        Engagement e = engagement("malformed");
        assertThatThrownBy(() -> coordinator.transition(e, EngagementState.COMPLETED, "test")).isInstanceOf(InvalidTransitionException.class);
        assertThat(e.getState()).isEqualTo(EngagementState.QUALIFYING);
    }
    @Test void defaultsPreserveLegacyTransitionsAndMalformedSnapshotsFail() {
        Engagement legacy = engagement(null);
        coordinator.selectLead(legacy, UUID.randomUUID());
        assertThat(legacy.getState()).isEqualTo(EngagementState.CLIENT_INTELLIGENCE);
        Engagement invalid = engagement(" ");
        assertThatThrownBy(() -> coordinator.selectLead(invalid, UUID.randomUUID())).isInstanceOf(RuntimeException.class);
        assertThat(invalid.getSelectedLeadId()).isNull();
        assertThat(invalid.getState()).isEqualTo(EngagementState.QUALIFYING);
    }
    @Test void researchCompletionGateAppliesAtInternalMilestone() {
        Engagement e = engagement(codec.encode(withGate(StageCapability.CLIENT_INTELLIGENCE, LifecycleConditionNode.leaf(MIN_EVIDENCE_COUNT, 3), true)));
        coordinator.selectLead(e, UUID.randomUUID());
        assertThatThrownBy(() -> coordinator.transition(e, EngagementState.HYPOTHESIS_READY, "test")).isInstanceOf(LifecycleGateNotSatisfiedException.class);
        assertThat(e.getState()).isEqualTo(EngagementState.CLIENT_INTELLIGENCE);
        assertThat(meters.get("engagement.lifecycle.transitions").tag("outcome", "blocked").counter().count()).isEqualTo(1);
    }
    @Test void requiredVirtualReviewCannotBeSkippedButOptionalReviewDoesNotBlock() {
        Engagement required = atMeeting(codec.encode(withGate(StageCapability.MEETING_REVIEW, LifecycleConditionNode.leaf(MIN_TRUST, 90), true)));
        assertThatThrownBy(() -> coordinator.transition(required, EngagementState.DISCOVERY_COMPLETE, "test")).isInstanceOf(LifecycleGateNotSatisfiedException.class);
        Engagement optional = atMeeting(codec.encode(withGate(StageCapability.MEETING_REVIEW, LifecycleConditionNode.leaf(MIN_TRUST, 90), false)));
        coordinator.transition(optional, EngagementState.DISCOVERY_COMPLETE, "test");
        assertThat(optional.getState()).isEqualTo(EngagementState.DISCOVERY_COMPLETE);
    }
    @Test void meetingFailureEscapesAuthoredGatesAndEvenMalformedSnapshot() {
        Engagement e = atMeeting("malformed");
        coordinator.transition(e, EngagementState.MEETING_FAILED, "safety");
        assertThat(e.getState()).isEqualTo(EngagementState.MEETING_FAILED);
    }
    @Test void selfTransitionDoesNotPrematurelyApplyCompletionGate() {
        Engagement e = engagement(codec.encode(withGate(StageCapability.OUTREACH, LifecycleConditionNode.leaf(OUTREACH_ACCEPTED), true)));
        e.selectLead(UUID.randomUUID()); e.transitionTo(EngagementState.HYPOTHESIS_READY,"setup"); e.transitionTo(EngagementState.OUTREACHING,"setup");
        coordinator.transition(e, EngagementState.OUTREACHING, "attempt");
        assertThat(e.getState()).isEqualTo(EngagementState.OUTREACHING);
    }
    @Test void failedReadModelNeverCompletesDownstreamStages() {
        Engagement e = atMeeting(null); e.transitionTo(EngagementState.MEETING_FAILED,"setup");
        var resolved = coordinator.resolve(e, LifecycleFacts.empty());
        assertThat(resolved.lifecycle().stages()).filteredOn(s -> s.capability() == StageCapability.PROPOSAL).allMatch(s -> s.status().equals("LOCKED"));
        assertThat(resolved.lifecycle().progressPercent()).isEqualTo(EngagementProgressCalculator.progressPercent(e.getState()));
    }
    private Engagement engagement(String snapshot) { return Engagement.start(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),null,null,snapshot); }
    private Engagement atMeeting(String snapshot) {
        Engagement e = engagement(snapshot); e.selectLead(UUID.randomUUID());
        for(var state : List.of(EngagementState.HYPOTHESIS_READY, EngagementState.OUTREACHING, EngagementState.MEETING_SECURED, EngagementState.PREPARING, EngagementState.IN_MEETING)) e.transitionTo(state,"setup");
        return e;
    }
    private ScenarioLifecycleDefinition withGate(StageCapability capability, LifecycleConditionNode gate, boolean required) {
        var defaults = ScenarioLifecycleDefinition.defaults();
        return new ScenarioLifecycleDefinition(1, defaults.stages().stream().map(s -> s.capability() == capability ?
            new ScenarioStageDefinition(s.key(),s.capability(),s.label(),s.description(),s.goal(),s.doneText(),s.nextText(),required,s.displayOrder(),s.entryCondition(),gate) : s).toList(), defaults.objectives());
    }
}
