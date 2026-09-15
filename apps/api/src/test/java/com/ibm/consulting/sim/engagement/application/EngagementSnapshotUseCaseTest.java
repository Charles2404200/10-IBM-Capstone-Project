package com.ibm.consulting.sim.engagement.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.lead.domain.LeadRepository;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.application.LifecycleDefinitionCodec;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioLifecycleDefinition;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EngagementSnapshotUseCaseTest {
    private final LifecycleDefinitionCodec codec = new LifecycleDefinitionCodec(new ObjectMapper());
    private final EngagementLifecycleCoordinator lifecycle = new EngagementLifecycleCoordinator(
            codec, ids -> Map.of(), new SimpleMeterRegistry());

    @Test
    void startingAnEngagementStoresTheResolvedDefinition() {
        Scenario scenario = Scenario.create("Lifecycle", "Technology", "Description", 3);
        var persona = scenario.addPersona("Client", "CIO", "Example", null, null, null, null);
        scenario.updateLifecycleDefinition(codec.encode(ScenarioLifecycleDefinition.defaults()));
        scenario.publish();
        EngagementRepository engagements = mock(EngagementRepository.class);
        ScenarioRepository scenarios = mock(ScenarioRepository.class);
        DifficultyProfileService difficulty = mock(DifficultyProfileService.class);
        LeadRepository leads = mock(LeadRepository.class);
        when(scenarios.findById(scenario.getId())).thenReturn(Optional.of(scenario));
        when(difficulty.forScenario(scenario)).thenReturn(DifficultyProfile.defaults(3, 3, 3, 3));
        when(difficulty.snapshot(any())).thenReturn("difficulty");

        new StartEngagementUseCase(engagements, scenarios, difficulty, leads, lifecycle)
                .execute(UUID.randomUUID(), scenario.getId(), persona.getId());

        ArgumentCaptor<Engagement> saved = ArgumentCaptor.forClass(Engagement.class);
        verify(engagements).save(saved.capture());
        assertThat(codec.decode(saved.getValue().getLifecycleDefinitionSnapshot())).isEqualTo(ScenarioLifecycleDefinition.defaults());
    }

    @Test
    void retryCopiesTheExactOriginalDefinitionSnapshot() {
        UUID userId = UUID.randomUUID();
        String snapshot = codec.encode(ScenarioLifecycleDefinition.defaults());
        Engagement failed = Engagement.start(userId, UUID.randomUUID(), UUID.randomUUID(), "difficulty", null, snapshot);
        failed.selectLead(UUID.randomUUID());
        for (var state : List.of(EngagementState.HYPOTHESIS_READY, EngagementState.OUTREACHING,
                EngagementState.MEETING_SECURED, EngagementState.PREPARING, EngagementState.IN_MEETING,
                EngagementState.MEETING_FAILED)) failed.transitionTo(state, "setup");
        EngagementRepository engagements = mock(EngagementRepository.class);
        when(engagements.findByIdAndUserIdForUpdate(failed.getId(), userId)).thenReturn(Optional.of(failed));
        when(engagements.findByUserId(userId)).thenReturn(List.of(failed));

        new RetryEngagementUseCase(engagements, lifecycle).execute(failed.getId(), userId);

        ArgumentCaptor<Engagement> saved = ArgumentCaptor.forClass(Engagement.class);
        verify(engagements).save(saved.capture());
        assertThat(saved.getValue().getLifecycleDefinitionSnapshot()).isEqualTo(snapshot);
        assertThat(saved.getValue().getDifficultyProfileSnapshot()).isEqualTo("difficulty");
    }
}
