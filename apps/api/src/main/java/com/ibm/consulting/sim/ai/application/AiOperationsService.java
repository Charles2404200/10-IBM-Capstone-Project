package com.ibm.consulting.sim.ai.application;

import com.ibm.consulting.sim.ai.domain.AiProvider;
import com.ibm.consulting.sim.ai.domain.AiTaskType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the read-only "AI Operations" snapshot surfaced to admins/reviewers
 * (§21/22 of the design doc): per-provider health, quota usage and circuit
 * state, plus the configured task routing table. Never exposed to learners.
 *
 * <p>Uses {@link ObjectProvider} for {@link AiProviderRouter} because that bean
 * only exists when the orchestration layer is active ({@code app.ai.mock-mode=false});
 * in mock mode this simply reports an empty snapshot with {@code mockMode=true}.
 */
@Service
public class AiOperationsService {

    private final ObjectProvider<AiProviderRouter> routerProvider;
    private final AiOperationsRecorder recorder;
    private final boolean mockMode;

    public AiOperationsService(
            ObjectProvider<AiProviderRouter> routerProvider,
            AiOperationsRecorder recorder,
            @Value("${app.ai.mock-mode:true}") boolean mockMode) {
        this.routerProvider = routerProvider;
        this.recorder = recorder;
        this.mockMode = mockMode;
    }

    public AiOperationsResponse snapshot() {
        AiProviderRouter router = routerProvider.getIfAvailable();
        if (router == null) {
            return new AiOperationsResponse(mockMode, List.of(), Map.of());
        }

        Map<String, Long> quotaUsage = new HashMap<>();
        Map<String, String> circuitState = new HashMap<>();
        Map<String, Boolean> availability = new HashMap<>();
        for (AiProvider provider : router.providers().values()) {
            quotaUsage.put(provider.id(), router.quotaStore().currentUsage(provider.id()));
            circuitState.put(provider.id(),
                    router.circuitBreakerRegistry().circuitBreaker(provider.id()).getState().name());
            availability.put(provider.id(), provider.isAvailable());
        }

        List<com.ibm.consulting.sim.ai.application.AiProviderStat> stats =
                recorder.snapshot(quotaUsage, router.dailyQuotaByProvider(), circuitState, availability);

        Map<String, List<String>> routing = new HashMap<>();
        for (Map.Entry<AiTaskType, List<String>> entry : router.routingTable().entrySet()) {
            routing.put(entry.getKey().configKey(), entry.getValue());
        }

        return new AiOperationsResponse(mockMode, stats, routing);
    }
}
