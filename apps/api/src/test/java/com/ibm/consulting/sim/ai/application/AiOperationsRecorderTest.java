package com.ibm.consulting.sim.ai.application;

import com.ibm.consulting.sim.ai.domain.AiTaskType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiOperationsRecorderTest {

    @Test
    void exportsProviderOutcomeAndLatencyWithLowCardinalityTags() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AiOperationsRecorder recorder = new AiOperationsRecorder(meterRegistry);

        recorder.recordSuccess("gemini-free", AiTaskType.CONVERSATION, 125, true);

        assertThat(meterRegistry.get("consulting.ai.provider.requests")
                .tags("provider", "gemini-free", "task", "conversation",
                        "outcome", "success", "fallback", "true")
                .counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get("consulting.ai.provider.latency")
                .tags("provider", "gemini-free", "task", "conversation",
                        "outcome", "success", "fallback", "true")
                .timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(125);
    }
}