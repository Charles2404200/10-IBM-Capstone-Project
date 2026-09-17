package com.ibm.consulting.sim.ai.application;

import com.ibm.consulting.sim.ai.domain.AiProvider;
import com.ibm.consulting.sim.ai.domain.AiProviderException;
import com.ibm.consulting.sim.ai.domain.AiTaskType;
import com.ibm.consulting.sim.ai.domain.LatencyTier;
import com.ibm.consulting.sim.ai.domain.ProviderCapabilities;
import com.ibm.consulting.sim.ai.domain.ReasoningTier;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderRouterQuotaTest {

    @Test
    void firstSuccessConsumesOnlyTheInvokedProviderQuota() {
        TrackingQuotaStore quotas = new TrackingQuotaStore();
        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            AiProviderRouter router = router(List.of(
                    provider("gemini-free", () -> "first", true),
                    provider("watsonx-granite", () -> "second", true),
                    provider("openrouter-free", () -> "third", true)), quotas,
                    CircuitBreakerRegistry.ofDefaults(), executor);

            assertThat(router.completeFirstValid("persona_dialogue", "prompt", raw -> raw, 500).value())
                    .isEqualTo("first");
            assertThat(quotas.usage()).containsOnly(Map.entry("gemini-free", 1L));
        }
    }

    @Test
    void fallbackConsumesOnlyProvidersThatAreActuallyInvoked() {
        TrackingQuotaStore quotas = new TrackingQuotaStore();
        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            AiProviderRouter router = router(List.of(
                    provider("gemini-free", () -> { throw new AiProviderException("failed"); }, true),
                    provider("watsonx-granite", () -> "second", true),
                    provider("openrouter-free", () -> "third", true)), quotas,
                    CircuitBreakerRegistry.ofDefaults(), executor);

            assertThat(router.completeFirstValid("persona_dialogue", "prompt", raw -> raw, 500).value())
                    .isEqualTo("second");
            assertThat(quotas.usage()).containsExactlyInAnyOrderEntriesOf(
                    Map.of("gemini-free", 1L, "watsonx-granite", 1L));
        }
    }

    @Test
    void openCircuitDoesNotConsumeQuotaForProviderThatIsNotInvoked() {
        TrackingQuotaStore quotas = new TrackingQuotaStore();
        CircuitBreakerRegistry breakers = CircuitBreakerRegistry.ofDefaults();
        breakers.circuitBreaker("gemini-free").transitionToOpenState();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            AiProviderRouter router = router(List.of(
                    provider("gemini-free", () -> "never", true),
                    provider("watsonx-granite", () -> "fallback", true)), quotas, breakers, executor);

            assertThat(router.completeFirstValid("persona_dialogue", "prompt", raw -> raw, 500).value())
                    .isEqualTo("fallback");
            assertThat(quotas.usage()).containsOnly(Map.entry("watsonx-granite", 1L));
        }
    }

    @Test
    void allUnavailableStillProducesTheExistingRouterFailureWithoutQuotaUse() {
        TrackingQuotaStore quotas = new TrackingQuotaStore();
        try (ExecutorService executor = Executors.newFixedThreadPool(1)) {
            AiProviderRouter router = router(
                    List.of(provider("gemini-free", () -> "never", false)), quotas,
                    CircuitBreakerRegistry.ofDefaults(), executor);

            assertThatThrownBy(() -> router.completeFirstValid(
                    "persona_dialogue", "prompt", raw -> raw, 500))
                    .isInstanceOf(AiProviderException.class);
            assertThat(quotas.usage()).isEmpty();
        }
    }

    private AiProviderRouter router(List<AiProvider> providers, AiQuotaStore quotas,
                                    CircuitBreakerRegistry breakers, ExecutorService executor) {
        String route = providers.stream().map(AiProvider::id).collect(java.util.stream.Collectors.joining(","));
        return new AiProviderRouter(
                providers, breakers, quotas, new AiOperationsRecorder(new SimpleMeterRegistry()),
                route, route, route, route, route, route,
                1_000, 1_000, 1_000, executor, false, 3);
    }

    private AiProvider provider(String id, Supplier<String> response, boolean available) {
        return new AiProvider() {
            @Override public String id() { return id; }
            @Override public boolean isAvailable() { return available; }
            @Override public ProviderCapabilities capabilities() {
                return new ProviderCapabilities(Set.of(AiTaskType.values()),
                        LatencyTier.LOW, ReasoningTier.HIGH, true);
            }
            @Override public String complete(String useCase, String prompt) { return response.get(); }
        };
    }

    private static final class TrackingQuotaStore implements AiQuotaStore {
        private final Map<String, Long> usage = new ConcurrentHashMap<>();
        @Override public boolean tryConsume(String providerId, long dailyLimit) {
            usage.merge(providerId, 1L, Long::sum);
            return true;
        }
        @Override public long currentUsage(String providerId) { return usage.getOrDefault(providerId, 0L); }
        Map<String, Long> usage() { return Map.copyOf(usage); }
    }
}
