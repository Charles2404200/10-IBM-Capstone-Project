package com.ibm.consulting.sim.ai.application;

import com.ibm.consulting.sim.ai.domain.AiModelGateway;
import com.ibm.consulting.sim.ai.domain.AiProvider;
import com.ibm.consulting.sim.ai.domain.AiProviderException;
import com.ibm.consulting.sim.ai.domain.AiResponseParser;
import com.ibm.consulting.sim.ai.domain.AiTaskType;
import com.ibm.consulting.sim.ai.domain.AiValidationException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    private final ExecutorService providerExecutor;
    private final boolean parallelEnabled;
    private final int parallelMaxCandidates;

    public AiProviderRouter(
            List<AiProvider> providers,
            CircuitBreakerRegistry circuitBreakerRegistry,
            AiQuotaStore quotaStore,
            AiOperationsRecorder operationsRecorder,
            @Value("${app.ai.routing.conversation:gemini-free,watsonx-granite,openrouter-free}") String conversationRoute,
            @Value("${app.ai.routing.classification:gemini-free,watsonx-granite,openrouter-free}") String classificationRoute,
            @Value("${app.ai.routing.client-intelligence:gemini-free,watsonx-granite,openrouter-free}") String clientIntelligenceRoute,
            @Value("${app.ai.routing.assessment:watsonx-granite,gemini-free,openrouter-free}") String assessmentRoute,
            @Value("${app.ai.routing.evidence_extraction:gemini-free,watsonx-granite,openrouter-free}") String evidenceRoute,
            @Value("${app.ai.providers.watsonx.daily-quota:100000}") long watsonxDailyQuota,
            @Value("${app.ai.providers.gemini.daily-quota:1000}") long geminiDailyQuota,
            @Value("${app.ai.providers.openrouter.daily-quota:50}") long openrouterDailyQuota,
            @Qualifier("aiProviderExecutor") ExecutorService providerExecutor,
            @Value("${app.ai.parallel.enabled:true}") boolean parallelEnabled,
            @Value("${app.ai.parallel.max-candidates:3}") int parallelMaxCandidates) {
        this.providersById = providers.stream().collect(Collectors.toMap(AiProvider::id, p -> p));
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.quotaStore = quotaStore;
        this.operationsRecorder = operationsRecorder;
        this.routingTable = Map.of(
                AiTaskType.CONVERSATION, parseCsv(conversationRoute),
                AiTaskType.CLASSIFICATION, parseCsv(classificationRoute),
                AiTaskType.CLIENT_INTELLIGENCE, parseCsv(clientIntelligenceRoute),
                AiTaskType.ASSESSMENT, parseCsv(assessmentRoute),
                AiTaskType.EVIDENCE_EXTRACTION, parseCsv(evidenceRoute));
        this.dailyQuotaByProvider = Map.of(
                "watsonx-granite", watsonxDailyQuota,
                "gemini-free", geminiDailyQuota,
                "openrouter-free", openrouterDailyQuota);
        this.providerExecutor = providerExecutor;
        this.parallelEnabled = parallelEnabled;
        this.parallelMaxCandidates = Math.max(1, parallelMaxCandidates);
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
                long latencyMs = System.currentTimeMillis() - start;
                operationsRecorder.recordSuccess(providerId, task, latencyMs, isFallback);
                logProviderAttempt(providerId, task, i + 1, latencyMs, isFallback, "success", null);
                return result;
            } catch (CallNotPermittedException circuitOpen) {
                log.debug("Circuit breaker open for provider {}, skipping for task {}", providerId, task);
            } catch (Exception e) {
                long latencyMs = System.currentTimeMillis() - start;
                operationsRecorder.recordFailure(providerId, task, latencyMs, isFallback);
                logProviderAttempt(providerId, task, i + 1, latencyMs, isFallback,
                        "failure", e.getClass().getSimpleName());
                lastFailure = (e instanceof AiProviderException ape) ? ape
                        : new AiProviderException("Provider " + providerId + " failed for use-case " + useCase, e);
                log.warn("Provider {} failed for use-case {}, trying next candidate: {}",
                        providerId, useCase, e.getMessage());
            }
        }

        throw lastFailure != null ? lastFailure
                : new AiProviderException("No available AI provider for task " + task + " (use-case " + useCase + ")");
    }

    /**
     * Runs all usable candidates for a task concurrently and returns only a
     * parser-validated result. This is deliberately here, below business
     * services but above individual providers: neither a model nor a page gets
     * to decide which response is safe to use.
     */
    public <T> AiValidatedResponse<T> completeFirstValid(String useCase, String prompt,
                                                          AiResponseParser<T> parser, long timeoutMs) {
        AiTaskType task = AiTaskType.fromUseCase(useCase);
        List<ProviderCandidate> candidates = usableCandidates(task);
        if (candidates.isEmpty()) {
            throw new AiProviderException("No available AI provider for task " + task + " (use-case " + useCase + ")");
        }
        if (!parallelEnabled || candidates.size() == 1) {
            return completeSequentially(useCase, prompt, parser, candidates);
        }

        CompletionService<ProviderAttempt<T>> completions = new ExecutorCompletionService<>(providerExecutor);
        List<Future<ProviderAttempt<T>>> futures = new ArrayList<>();
        for (ProviderCandidate candidate : candidates) {
            futures.add(completions.submit(() -> invokeAndValidate(candidate, task, useCase, prompt, parser)));
        }

        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        ProviderAttempt<T> preferredResult = null;
        AiValidationException lastValidationFailure = null;
        AiProviderException lastProviderFailure = null;
        try {
            for (int completed = 0; completed < candidates.size(); completed++) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) break;
                Future<ProviderAttempt<T>> future = completions.poll(remainingNanos, TimeUnit.NANOSECONDS);
                if (future == null) break;
                ProviderAttempt<T> attempt = future.get();
                if (!attempt.succeeded()) {
                    if (attempt.failure() instanceof AiValidationException validationFailure) {
                        lastValidationFailure = validationFailure;
                    } else if (attempt.failure() instanceof AiProviderException providerFailure) {
                        lastProviderFailure = providerFailure;
                    }
                    continue;
                }

                if (task != AiTaskType.ASSESSMENT || attempt.candidate().priority() == 0) {
                    return new AiValidatedResponse<>(attempt.value(), attempt.candidate().provider().id());
                }
                if (preferredResult == null || attempt.candidate().priority() < preferredResult.candidate().priority()) {
                    preferredResult = attempt;
                }
            }
            if (preferredResult != null) {
                return new AiValidatedResponse<>(preferredResult.value(), preferredResult.candidate().provider().id());
            }
            if (lastValidationFailure != null) throw lastValidationFailure;
            if (lastProviderFailure != null) throw lastProviderFailure;
            throw new AiProviderException("All provider calls exceeded the " + timeoutMs + "ms budget for task " + task);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Parallel provider execution was interrupted", interrupted);
        } catch (java.util.concurrent.ExecutionException executionFailure) {
            throw new AiProviderException("Parallel provider execution failed", executionFailure.getCause());
        } finally {
            futures.forEach(future -> future.cancel(true));
        }
    }

    private List<ProviderCandidate> usableCandidates(AiTaskType task) {
        List<ProviderCandidate> candidates = new ArrayList<>();
        for (String providerId : routingTable.getOrDefault(task, List.of())) {
            if (candidates.size() >= parallelMaxCandidates) break;
            AiProvider provider = providersById.get(providerId);
            if (provider == null || !provider.isAvailable() || !provider.capabilities().supports(task)) continue;
            if (!quotaStore.tryConsume(providerId, dailyQuotaByProvider.getOrDefault(providerId, 0L))) continue;
            candidates.add(new ProviderCandidate(candidates.size(), provider));
        }
        return candidates;
    }

    private <T> AiValidatedResponse<T> completeSequentially(String useCase, String prompt, AiResponseParser<T> parser,
                                                             List<ProviderCandidate> candidates) {
        AiValidationException lastValidationFailure = null;
        AiProviderException lastProviderFailure = null;
        AiTaskType task = AiTaskType.fromUseCase(useCase);
        for (ProviderCandidate candidate : candidates) {
            ProviderAttempt<T> attempt = invokeAndValidate(candidate, task, useCase, prompt, parser);
            if (attempt.succeeded()) {
                return new AiValidatedResponse<>(attempt.value(), candidate.provider().id());
            }
            if (attempt.failure() instanceof AiValidationException validationFailure) {
                lastValidationFailure = validationFailure;
            } else if (attempt.failure() instanceof AiProviderException providerFailure) {
                lastProviderFailure = providerFailure;
            }
        }
        if (lastValidationFailure != null) throw lastValidationFailure;
        throw lastProviderFailure != null ? lastProviderFailure
                : new AiProviderException("No usable AI provider for task " + task);
    }

    private <T> ProviderAttempt<T> invokeAndValidate(ProviderCandidate candidate, AiTaskType task, String useCase,
                                                      String prompt, AiResponseParser<T> parser) {
        long start = System.currentTimeMillis();
        AiProvider provider = candidate.provider();
        try {
            CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker(provider.id());
            String raw = breaker.executeSupplier(() -> provider.complete(useCase, prompt));
            T parsed = parser.parse(raw);
            long latencyMs = System.currentTimeMillis() - start;
            operationsRecorder.recordSuccess(provider.id(), task, latencyMs, candidate.priority() > 0);
            logProviderAttempt(provider.id(), task, candidate.priority() + 1, latencyMs,
                    candidate.priority() > 0, "success", null);
            return ProviderAttempt.success(candidate, parsed);
        } catch (Exception failure) {
            long latencyMs = System.currentTimeMillis() - start;
            operationsRecorder.recordFailure(provider.id(), task, latencyMs, candidate.priority() > 0);
            String error = failure instanceof CallNotPermittedException ? "circuit_open" : failure.getClass().getSimpleName();
            logProviderAttempt(provider.id(), task, candidate.priority() + 1, latencyMs,
                    candidate.priority() > 0, "failure", error);
            AiProviderException normalized = failure instanceof AiProviderException providerFailure
                    ? providerFailure
                    : new AiProviderException("Provider " + provider.id() + " failed for use-case " + useCase, failure);
            RuntimeException normalizedFailure = failure instanceof AiValidationException validationFailure
                    ? validationFailure
                    : normalized;
            return ProviderAttempt.failure(candidate, normalizedFailure);
        }
    }

    private record ProviderCandidate(int priority, AiProvider provider) {
    }

    private record ProviderAttempt<T>(ProviderCandidate candidate, T value, RuntimeException failure) {
        static <T> ProviderAttempt<T> success(ProviderCandidate candidate, T value) {
            return new ProviderAttempt<>(candidate, value, null);
        }

        static <T> ProviderAttempt<T> failure(ProviderCandidate candidate, RuntimeException failure) {
            return new ProviderAttempt<>(candidate, null, failure);
        }

        boolean succeeded() {
            return failure == null;
        }
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

    boolean parallelEnabled() {
        return parallelEnabled;
    }

    int parallelMaxCandidates() {
        return parallelMaxCandidates;
    }

    AiQuotaStore quotaStore() {
        return quotaStore;
    }

    private void logProviderAttempt(String providerId, AiTaskType task, int attempt,
                                    long latencyMs, boolean fallback, String outcome, String errorType) {
        var event = log.atInfo()
                .addKeyValue("event", "AI_PROVIDER_ATTEMPT_COMPLETED")
                .addKeyValue("provider", providerId)
                .addKeyValue("task", task.name().toLowerCase())
                .addKeyValue("attempt", attempt)
                .addKeyValue("latencyMs", latencyMs)
                .addKeyValue("fallback", fallback)
                .addKeyValue("outcome", outcome);
        if (errorType != null) {
            event.addKeyValue("errorType", errorType);
        }
        event.log("AI provider attempt completed");
    }

    private static List<String> parseCsv(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
