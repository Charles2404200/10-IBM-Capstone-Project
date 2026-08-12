package com.ibm.consulting.sim.ai.application;

import com.ibm.consulting.sim.ai.domain.AiProvider;
import com.ibm.consulting.sim.ai.domain.AiResponseParser;
import com.ibm.consulting.sim.ai.domain.AiTaskType;
import com.ibm.consulting.sim.ai.domain.LatencyTier;
import com.ibm.consulting.sim.ai.domain.ProviderCapabilities;
import com.ibm.consulting.sim.ai.domain.ReasoningTier;
import com.ibm.consulting.sim.ai.infrastructure.InMemoryAiQuotaStore;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiProviderRouterParallelTest {

    private static final AiResponseParser<String> VALID_PARSER = raw -> {
        if (!raw.startsWith("valid:")) throw new com.ibm.consulting.sim.ai.domain.AiValidationException("invalid schema");
        return raw.substring("valid:".length());
    };

    @Test
    void selectsFirstSchemaValidParallelResponseRatherThanFirstRawResponse() {
        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            AiProviderRouter router = router(
                    List.of(
                            provider("gemini-free", "invalid", 5),
                            provider("watsonx-granite", "valid:slow", 80),
                            provider("openrouter-free", "valid:fast", 10)),
                    executor);

            AiValidatedResponse<String> result = router.completeFirstValid(
                    "persona_dialogue", "prompt", VALID_PARSER, 500);

            assertEquals("fast", result.value());
            assertEquals("openrouter-free", result.providerId());
        }
    }

    @Test
    void assessmentWaitsForPreferredReasoningProviderWithinDeadline() {
        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            AiProviderRouter router = router(
                    List.of(
                            provider("gemini-free", "valid:fast", 10),
                            provider("watsonx-granite", "valid:preferred", 80),
                            provider("openrouter-free", "valid:other", 15)),
                    executor);

            AiValidatedResponse<String> result = router.completeFirstValid(
                    "assessment_feedback", "prompt", VALID_PARSER, 500);

            assertEquals("preferred", result.value());
            assertEquals("watsonx-granite", result.providerId());
        }
    }

    private AiProviderRouter router(List<AiProvider> providers, ExecutorService executor) {
        return new AiProviderRouter(
                providers,
                CircuitBreakerRegistry.ofDefaults(),
                new InMemoryAiQuotaStore(),
                new AiOperationsRecorder(new SimpleMeterRegistry()),
                "gemini-free,watsonx-granite,openrouter-free",
                "gemini-free,watsonx-granite,openrouter-free",
                "gemini-free,watsonx-granite,openrouter-free",
                "watsonx-granite,gemini-free,openrouter-free",
                "gemini-free,watsonx-granite,openrouter-free",
                "gemini-free,watsonx-granite,openrouter-free",
                1000, 1000, 1000, executor, true, 3);
    }

    private AiProvider provider(String id, String response, long delayMs) {
        return new AiProvider() {
            @Override public String id() { return id; }
            @Override public boolean isAvailable() { return true; }
            @Override public ProviderCapabilities capabilities() {
                return new ProviderCapabilities(Set.of(AiTaskType.values()), LatencyTier.LOW, ReasoningTier.HIGH, true);
            }
            @Override public String complete(String useCase, String prompt) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                return response;
            }
        };
    }
}
