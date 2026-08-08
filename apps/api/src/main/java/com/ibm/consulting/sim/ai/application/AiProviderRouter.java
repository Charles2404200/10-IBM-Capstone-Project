package com.ibm.consulting.sim.ai.application;

import com.ibm.consulting.sim.ai.domain.AiModelGateway;
import com.ibm.consulting.sim.ai.domain.AiProvider;
import com.ibm.consulting.sim.ai.domain.AiProviderException;
import com.ibm.consulting.sim.ai.domain.AiTaskType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The "Provider Router" from the AI orchestration design: picks, for a given
 * use-case, an ordered list of candidate {@link AiProvider}s (configured per
 * {@link AiTaskType} — capability-based routing, §5), and tries them in
 * priority order, skipping/falling through on unavailability, exhausted
 * quota, an open circuit breaker, or a call failure.
 *
 * <p>Implements {@link AiModelGateway} so it is a drop-in replacement for
 * {@code MockAiGateway} — {@link AiOrchestrationService} (timeout enforcement,
 * schema validation, repair-retry, fallback, audit tracing) needs zero changes
 * whether it is talking to a single mock gateway or this multi-provider router.
 *
 * <p><b>Deliberately not random fallback</b> (§4 of the design doc): the
 * candidate order for each task is explicit, operator-configured
 * ({@code app.ai.routing.<task>}), not "whichever free model happens to
 * respond" — so a persona's conversational voice doesn't shift model-to-model
 * mid-engagement any more than strictly necessary (only on an actual outage).
 */
@Component
@ConditionalOnProperty(name = "app.ai.mock-mode", havingValue = "false")
public class AiProviderRouter implements AiModelGateway {

    private static final Logger log = LoggerFactory.getLogger(AiProviderRouter.class);

    private final Map<String, AiProvider> providersById;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final AiQuotaStore quotaStore;
    private final AiOperationsRecorder operationsRecorder;
    private final Map<AiTaskType, List<String>> routingTable;
    private final Map<String, Long> dailyQuotaByProvider;

    public AiProviderRouter(
            List<AiProvider> providers,
            CircuitBreakerRegistry circuitBreakerRegistry,
            AiQuotaStore quotaStore,
            AiOperationsRecorder operationsRecorder,
            @Value("${app.ai.routing.conversation:gemini-free,watsonx-granite,openrouter-free}") String conversationRoute,
            @Value("${app.ai.routing.classification:gemini-free,watsonx-granite,openrouter-free}") String classificationRoute,
            @Value("${app.ai.routing.assessment:watsonx-granite,gemini-free,openrouter-free}") String assessmentRoute,
            @Value("${app.ai.routing.evidence_extraction:gemini-free,watsonx-granite,openrouter-free}") String evidenceRoute,
            @Value("${app.ai.providers.watsonx.daily-quota:100000}") long watsonxDailyQuota,
            @Value("${app.ai.providers.gemini.daily-quota:1000}") long geminiDailyQuota,
            @Value("${app.ai.providers.openrouter.daily-quota:50}") long openrouterDailyQuota) {
        this.providersById = providers.stream().collect(Collectors.toMap(AiProvider::id, p -> p));
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.quotaStore = quotaStore;
        this.operationsRecorder = operationsRecorder;
        this.routingTable = Map.of(
                AiTaskType.CONVERSATION, parseCsv(conversationRoute),
                AiTaskType.CLASSIFICATION, parseCsv(classificationRoute),
                AiTaskType.ASSESSMENT, parseCsv(assessmentRoute),
                AiTaskType.EVIDENCE_EXTRACTION, parseCsv(evidenceRoute));
        this.dailyQuotaByProvider = Map.of(
                "watsonx-granite", watsonxDailyQuota,
                "gemini-free", geminiDailyQuota,
                "openrouter-free", openrouterDailyQuota);
    }

    @Override
    public String complete(String useCase, String prompt) {
        AiTaskType task = AiTaskType.fromUseCase(useCase);
        List<String> candidateIds = routingTable.getOrDefault(task, List.of());
        AiProviderException lastFailure = null;

        for (int i = 0; i < candidateIds.size(); i++) {
            String providerId = candidateIds.get(i);
            AiProvider provider = providersById.get(providerId);
            if (provider == null) {
                log.warn("app.ai.routing for task {} references unknown provider id '{}' — check configuration",
                        task, providerId);
                continue;
            }
            if (!provider.isAvailable()) {
                continue; // no credentials configured yet — silent skip, this is expected, not a failure
            }
            long quotaLimit = dailyQuotaByProvider.getOrDefault(providerId, 0L);
            if (!quotaStore.tryConsume(providerId, quotaLimit)) {
                log.debug("Provider {} has exhausted its daily free-tier quota, skipping for task {}", providerId, task);
                continue;
            }

            boolean isFallback = i > 0;
            CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker(providerId);
            long start = System.currentTimeMillis();
            try {
                String result = breaker.executeSupplier(() -> provider.complete(useCase, prompt));
                operationsRecorder.recordSuccess(providerId, task, System.currentTimeMillis() - start, isFallback);
                return result;
            } catch (CallNotPermittedException circuitOpen) {
                log.debug("Circuit breaker open for provider {}, skipping for task {}", providerId, task);
            } catch (Exception e) {
                operationsRecorder.recordFailure(providerId, task, System.currentTimeMillis() - start);
                lastFailure = (e instanceof AiProviderException ape) ? ape
                        : new AiProviderException("Provider " + providerId + " failed for use-case " + useCase, e);
                log.warn("Provider {} failed for use-case {}, trying next candidate: {}",
                        providerId, useCase, e.getMessage());
            }
        }

        throw lastFailure != null ? lastFailure
                : new AiProviderException("No available AI provider for task " + task + " (use-case " + useCase + ")");
    }

    /** Read-only view of the configured routing table, for the admin AI Operations endpoint. */
    Map<AiTaskType, List<String>> routingTable() {
        return routingTable;
    }

    /** Read-only view of registered providers, for the admin AI Operations endpoint. */
    Map<String, AiProvider> providers() {
        return providersById;
    }

    CircuitBreakerRegistry circuitBreakerRegistry() {
        return circuitBreakerRegistry;
    }

    Map<String, Long> dailyQuotaByProvider() {
        return dailyQuotaByProvider;
    }

    AiQuotaStore quotaStore() {
        return quotaStore;
    }

    private static List<String> parseCsv(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
