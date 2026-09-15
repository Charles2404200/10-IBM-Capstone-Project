package com.ibm.consulting.sim.scenario.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.scenario.domain.*;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditAction;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScenarioLifecycleServiceTest {
    private final ScenarioRepository repository = mock(ScenarioRepository.class);
    private final AuditLogger audit = mock(AuditLogger.class);
    private final LifecycleDefinitionCodec codec = new LifecycleDefinitionCodec(new ObjectMapper());
    private final ScenarioLifecycleService service = new ScenarioLifecycleService(repository, codec, audit);
    private Scenario scenario;

    @BeforeEach
    void setup() {
        scenario = Scenario.create("Lifecycle", "Technology", "Description", 3);
        ReflectionTestUtils.setField(scenario, "version", 4L);
        when(repository.findById(scenario.getId())).thenReturn(Optional.of(scenario));
    }

    @Test
    void legacyReadResolvesDefaultsAndCurrentVersion() {
        var view = service.get(scenario.getId());
        assertThat(view.definition().stages()).hasSize(10);
        assertThat(view.version()).isEqualTo(4L);
        verify(repository, never()).save(any());
    }

    @Test
    void draftSaveValidatesPersistsFlushesAndAuditsSafeSummary() {
        when(repository.save(scenario)).thenReturn(scenario);
        doAnswer(invocation -> { ReflectionTestUtils.setField(scenario, "version", 5L); return null; })
                .when(repository).flush();
        var result = service.update(scenario.getId(), new UpdateScenarioLifecycleRequest(ScenarioLifecycleDefinition.defaults(), 4L));
        assertThat(result.version()).isEqualTo(5L);
        assertThat(codec.decode(scenario.getLifecycleDefinition())).isEqualTo(result.definition());
        var sequence = inOrder(repository, audit);
        sequence.verify(repository).findById(scenario.getId());
        sequence.verify(repository).save(scenario);
        sequence.verify(repository).flush();
        sequence.verify(audit).recordAdmin(eq(AuditAction.ADMIN_SCENARIO_LIFECYCLE_CHANGED), eq("SCENARIO"),
                eq(scenario.getId().toString()), eq("revision=1; stages=10; objectives=0"));
    }

    @Test
    void staleAndDuplicateRequestsCannotOverwriteNewerContent() {
        assertThatThrownBy(() -> service.update(scenario.getId(),
                new UpdateScenarioLifecycleRequest(ScenarioLifecycleDefinition.defaults(), 3L)))
                .isInstanceOf(ScenarioLifecycleConflictException.class);
        verify(repository, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void activeAndArchivedDefinitionsAreImmutable() {
        scenario.publish();
        assertThatThrownBy(() -> service.update(scenario.getId(),
                new UpdateScenarioLifecycleRequest(ScenarioLifecycleDefinition.defaults(), 4L)))
                .isInstanceOf(Scenario.ScenarioNotEditableException.class);
        scenario.archive();
        assertThatThrownBy(() -> service.update(scenario.getId(),
                new UpdateScenarioLifecycleRequest(ScenarioLifecycleDefinition.defaults(), 4L)))
                .isInstanceOf(Scenario.ScenarioNotEditableException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void failedFlushDoesNotProduceAnAuditRecord() {
        doThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(Scenario.class, scenario.getId()))
                .when(repository).flush();
        assertThatThrownBy(() -> service.update(scenario.getId(),
                new UpdateScenarioLifecycleRequest(ScenarioLifecycleDefinition.defaults(), 4L)))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
        verifyNoInteractions(audit);
    }
}
