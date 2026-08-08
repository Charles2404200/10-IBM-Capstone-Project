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
    private final long conversationTimeoutMs;
    private final long clientIntelligenceTimeoutMs;
    private final long classificationTimeoutMs;
    private final String modelId;

    public AiOrchestrationService(AiModelGateway gateway,
                                   AiTraceRepository traceRepository,
                                   @Qualifier("aiGatewayExecutor") ExecutorService executor,
                                   @Value("${app.ai.timeout-ms:15000}") long timeoutMs,
                                   @Value("${app.ai.conversation-timeout-ms:4000}") long conversationTimeoutMs,
                                   @Value("${app.ai.client-intelligence-timeout-ms:1200}") long clientIntelligenceTimeoutMs,
                                   @Value("${app.ai.classification-timeout-ms:2500}") long classificationTimeoutMs,
                                   @Value("${app.watsonx.model-id}") String modelId) {
        this.gateway = gateway;
        this.traceRepository = traceRepository;
        this.executor = executor;
        this.timeoutMs = timeoutMs;
        this.conversationTimeoutMs = conversationTimeoutMs;
        this.clientIntelligenceTimeoutMs = clientIntelligenceTimeoutMs;
        this.classificationTimeoutMs = classificationTimeoutMs;
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
        long budgetMs = budgetFor(AiTaskType.fromUseCase(useCase));
        long deadline = start + budgetMs;
        try {
            String raw = callWithTimeout(useCase, prompt, remainingMillis(deadline));
            try {
                T result = parser.parse(raw);
                trace(useCase, engagementId, promptVersion, start, AiTraceStatus.SUCCESS, null);
                return result;
            } catch (AiValidationException validationFailure) {
                log.warn("AI response failed validation for use-case {}: {}", useCase, validationFailure.getMessage());
                return repairThenFallback(
                        useCase, engagementId, prompt, promptVersion, parser, fallback, start, deadline);
            }
        } catch (TimeoutException | ExecutionException | InterruptedException e) {
            log.error("AI gateway call failed for use-case {}", useCase, e);
            trace(useCase, engagementId, promptVersion, start, AiTraceStatus.ERROR, e.getMessage());
            return fallback.get();
        }
    }

    private long budgetFor(AiTaskType taskType) {
        return switch (taskType) {
            case CONVERSATION -> conversationTimeoutMs;
            case CLIENT_INTELLIGENCE -> clientIntelligenceTimeoutMs;
            case CLASSIFICATION, EVIDENCE_EXTRACTION -> classificationTimeoutMs;
            case ASSESSMENT -> timeoutMs;
        };
    }

    private <T> T repairThenFallback(String useCase, UUID engagementId, String originalPrompt, int promptVersion,
                                      AiResponseParser<T> parser, Supplier<T> fallback, long start, long deadline) {
        String repairPrompt = originalPrompt
                + "\n\nYour previous response was invalid. Return ONLY valid JSON matching the required schema exactly.";
        try {
            String repaired = callWithTimeout(useCase, repairPrompt, remainingMillis(deadline));
            T result = parser.parse(repaired);
            trace(useCase, engagementId, promptVersion, start, AiTraceStatus.REPAIRED, null);
            return result;
        } catch (Exception repairFailure) {
            log.error("AI repair attempt failed for use-case {}, using fallback", useCase, repairFailure);
            trace(useCase, engagementId, promptVersion, start, AiTraceStatus.FALLBACK, repairFailure.getMessage());
            return fallback.get();
        }
    }

    private String callWithTimeout(String useCase, String prompt, long callTimeoutMs)
            throws TimeoutException, ExecutionException, InterruptedException {
        Future<String> future = executor.submit(() -> gateway.complete(useCase, prompt));
        try {
            return future.get(callTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException e) {
            future.cancel(true);
            throw e;
        }
    }

    private long remainingMillis(long deadline) throws TimeoutException {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) {
            throw new TimeoutException("AI request exhausted its latency budget");
        }
        return remaining;
    }

    private void trace(String useCase, UUID engagementId, int promptVersion, long start,
                        AiTraceStatus status, String errorMessage) {
        long latency = System.currentTimeMillis() - start;
        traceRepository.save(AiTrace.record(useCase, engagementId, modelId, promptVersion, latency, status, errorMessage));
    }
}
