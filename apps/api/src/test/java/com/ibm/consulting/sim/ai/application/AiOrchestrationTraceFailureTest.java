package com.ibm.consulting.sim.ai.application;

import com.ibm.consulting.sim.ai.domain.AiModelGateway;
import com.ibm.consulting.sim.ai.domain.AiProviderException;
import com.ibm.consulting.sim.ai.domain.AiTraceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiOrchestrationTraceFailureTest {

    @Test
    void successfulAiResultSurvivesTracePersistenceFailure() {
        AiModelGateway gateway = mock(AiModelGateway.class);
        when(gateway.complete(anyString(), anyString())).thenReturn("valid");
        AiTraceRecorder traces = failingRecorder();
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            AiOrchestrationService service = service(gateway, traces, executor);

            assertThat(service.execute("persona_dialogue", UUID.randomUUID(), "prompt", 1,
                    raw -> raw, () -> "fallback")).isEqualTo("valid");
        }
    }

    @Test
    void deterministicFallbackSurvivesTracePersistenceFailure() {
        AiModelGateway gateway = mock(AiModelGateway.class);
        when(gateway.complete(anyString(), anyString())).thenThrow(new AiProviderException("unavailable"));
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            AiOrchestrationService service = service(gateway, failingRecorder(), executor);

            assertThat(service.execute("persona_dialogue", UUID.randomUUID(), "prompt", 1,
                    raw -> raw, () -> "fallback")).isEqualTo("fallback");
        }
    }

    @Test
    void successfulTraceIsStillRecorded() {
        AiModelGateway gateway = mock(AiModelGateway.class);
        when(gateway.complete(anyString(), anyString())).thenReturn("valid");
        AiTraceRecorder traces = mock(AiTraceRecorder.class);
        UUID engagementId = UUID.randomUUID();
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            AiOrchestrationService service = service(gateway, traces, executor);

            service.execute("persona_dialogue", engagementId, "prompt", 3, raw -> raw, () -> "fallback");

            verify(traces).record(eq("persona_dialogue"), eq(engagementId), eq("model"), eq(3),
                    anyLong(), eq(AiTraceStatus.SUCCESS), any());
        }
    }

    @Test
    void unrelatedFallbackPersistenceFailureIsNotSwallowed() {
        AiModelGateway gateway = mock(AiModelGateway.class);
        when(gateway.complete(anyString(), anyString())).thenThrow(new AiProviderException("unavailable"));
        DataIntegrityViolationException businessFailure = new DataIntegrityViolationException("business write");
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            AiOrchestrationService service = service(gateway, mock(AiTraceRecorder.class), executor);

            assertThatThrownBy(() -> service.execute("persona_dialogue", UUID.randomUUID(), "prompt", 1,
                    raw -> raw, () -> { throw businessFailure; }))
                    .isSameAs(businessFailure);
        }
    }

    private AiTraceRecorder failingRecorder() {
        AiTraceRecorder recorder = mock(AiTraceRecorder.class);
        doThrow(new DataIntegrityViolationException("trace write")).when(recorder)
                .record(anyString(), any(), anyString(), anyInt(), anyLong(), any(), any());
        return recorder;
    }

    @SuppressWarnings("unchecked")
    private AiOrchestrationService service(AiModelGateway gateway, AiTraceRecorder traces,
                                           ExecutorService executor) {
        ObjectProvider<AiProviderRouter> routers = mock(ObjectProvider.class);
        when(routers.getIfAvailable()).thenReturn(null);
        return new AiOrchestrationService(
                gateway, routers, traces, executor, 1_000, 1_000, 1_000, 1_000, "model");
    }
}
