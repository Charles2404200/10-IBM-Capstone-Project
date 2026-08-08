package com.ibm.consulting.sim.ai.application;

import com.ibm.consulting.sim.ai.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Orchestrates a single AI invocation end to end: timeout enforcement, schema
 * validation, one repair-prompt retry, safe fallback, and audit tracing.
 * This is the only place in the codebase allowed to call {@link AiModelGateway}
 * directly — every module goes through here (Facade / Template Method pattern).
 */
@Service
public class AiOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AiOrchestrationService.class);

    private final AiModelGateway gateway;
    private final AiTraceRepository traceRepository;
    private final ExecutorService executor;
    private final long timeoutMs;
    private final String modelId;

    public AiOrchestrationService(AiModelGateway gateway,
                                   AiTraceRepository traceRepository,
                                   @Qualifier("aiGatewayExecutor") ExecutorService executor,
                                   @Value("${app.ai.timeout-ms:15000}") long timeoutMs,
                                   @Value("${app.watsonx.model-id}") String modelId) {
        this.gateway = gateway;
        this.traceRepository = traceRepository;
        this.executor = executor;
        this.timeoutMs = timeoutMs;
        this.modelId = modelId;
    }

    /**
     * Executes {@code useCase} with the given prompt, validating the response with
     * {@code parser}. On validation failure, retries once with a repair prompt.
     * On repeated failure or timeout, returns {@code fallback} and records the trace
     * as FALLBACK so the incident is auditable without failing the learner's request.
     */
    public <T> T execute(String useCase, UUID engagementId, String prompt, int promptVersion,
                          AiResponseParser<T> parser, Supplier<T> fallback) {
        long start = System.currentTimeMillis();
        try {
            String raw = callWithTimeout(useCase, prompt);
            try {
                T result = parser.parse(raw);
                trace(useCase, engagementId, promptVersion, start, AiTraceStatus.SUCCESS, null);
                return result;
            } catch (AiValidationException validationFailure) {
                log.warn("AI response failed validation for use-case {}: {}", useCase, validationFailure.getMessage());
                return repairThenFallback(useCase, engagementId, prompt, promptVersion, parser, fallback, start);
            }
        } catch (TimeoutException | ExecutionException | InterruptedException e) {
            log.error("AI gateway call failed for use-case {}", useCase, e);
            trace(useCase, engagementId, promptVersion, start, AiTraceStatus.ERROR, e.getMessage());
            return fallback.get();
        }
    }

    private <T> T repairThenFallback(String useCase, UUID engagementId, String originalPrompt, int promptVersion,
                                      AiResponseParser<T> parser, Supplier<T> fallback, long start) {
        String repairPrompt = originalPrompt
                + "\n\nYour previous response was invalid. Return ONLY valid JSON matching the required schema exactly.";
        try {
            String repaired = callWithTimeout(useCase, repairPrompt);
            T result = parser.parse(repaired);
            trace(useCase, engagementId, promptVersion, start, AiTraceStatus.REPAIRED, null);
            return result;
        } catch (Exception repairFailure) {
            log.error("AI repair attempt failed for use-case {}, using fallback", useCase, repairFailure);
            trace(useCase, engagementId, promptVersion, start, AiTraceStatus.FALLBACK, repairFailure.getMessage());
            return fallback.get();
        }
    }

    private String callWithTimeout(String useCase, String prompt) throws TimeoutException, ExecutionException, InterruptedException {
        Future<String> future = executor.submit(() -> gateway.complete(useCase, prompt));
        return future.get(timeoutMs, TimeUnit.MILLISECONDS);
    }

    private void trace(String useCase, UUID engagementId, int promptVersion, long start,
                        AiTraceStatus status, String errorMessage) {
        long latency = System.currentTimeMillis() - start;
        traceRepository.save(AiTrace.record(useCase, engagementId, modelId, promptVersion, latency, status, errorMessage));
    }
}
