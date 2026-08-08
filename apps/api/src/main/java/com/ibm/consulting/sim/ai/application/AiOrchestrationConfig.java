package com.ibm.consulting.sim.ai.application;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Wiring for the AI provider orchestration layer. Kept separate from
 * {@code shared.config} because it is specific to the {@code ai} module and its
 * settings ({@code app.ai.circuit-breaker.*}) are unrelated to the cross-cutting
 * cache/async config there.
 */
@Configuration
public class AiOrchestrationConfig {

    /**
     * One registry shared by every {@link com.ibm.consulting.sim.ai.domain.AiProvider} —
     * {@link AiProviderRouter} obtains (and lazily creates, on first use) a
     * per-provider {@code CircuitBreaker} instance from this registry keyed by
     * provider id. After {@code failureRateThreshold}% of the last
     * {@code slidingWindowSize} calls to a provider fail, its circuit opens and
     * the router stops calling it for {@code waitDurationInOpenState} — instead of
     * every subsequent learner request waiting out that provider's full timeout
     * before falling back (§6 of the design doc).
     */
    @Bean
    public CircuitBreakerRegistry aiCircuitBreakerRegistry(
            @Value("${app.ai.circuit-breaker.failure-rate-threshold:50}") float failureRateThreshold,
            @Value("${app.ai.circuit-breaker.wait-duration-seconds:60}") long waitDurationSeconds,
            @Value("${app.ai.circuit-breaker.sliding-window-size:10}") int slidingWindowSize,
            @Value("${app.ai.circuit-breaker.minimum-calls:5}") int minimumNumberOfCalls) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(Duration.ofSeconds(waitDurationSeconds))
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .permittedNumberOfCallsInHalfOpenState(minimumNumberOfCalls)
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    public AiOperationsRecorder aiOperationsRecorder() {
        return new AiOperationsRecorder();
    }
}
