package com.ibm.consulting.sim.ai.domain;

/**
 * A single, swappable LLM vendor integration (watsonx.ai, Gemini, OpenRouter, ...).
 *
 * <p>Deliberately <strong>not</strong> a subtype of {@link AiModelGateway}: exactly one
 * {@code AiModelGateway} bean exists at a time (either {@code MockAiGateway} in mock mode,
 * or {@code AiProviderRouter} in orchestrated mode), while zero or more {@code AiProvider}
 * beans can coexist and be selected between at runtime. This separation is what lets
 * {@link com.ibm.consulting.sim.ai.application.AiOrchestrationService} keep depending on a
 * single {@code AiModelGateway} injection point, unmodified, no matter how many real
 * providers are registered behind the router.
 *
 * <p>A provider must never throw for "the model disagreed with the schema" — that is the
 * orchestration layer's job (validation + repair-retry). A provider throws
 * {@link AiProviderException} only for infrastructure-level failure (network error,
 * non-2xx response, timeout, rate limit) so the router knows to try the next candidate
 * rather than treat it as a content problem.
 */
public interface AiProvider {

    /** Stable identifier used in configuration (routing lists, quotas) and observability. */
    String id();

    /**
     * Sends a prompt and returns the raw JSON string response.
     *
     * @throws AiProviderException on any infrastructure-level failure (network, HTTP, timeout).
     */
    String complete(String useCase, String prompt);

    /**
     * Whether this provider is currently usable — has credentials configured. Circuit-breaker
     * state and quota exhaustion are checked separately by the router (they are cross-cutting
     * concerns, not something each provider needs to know about itself), but a provider with
     * no API key at all (the "leave room for later" placeholder case) reports itself
     * unavailable so the router can silently skip it without a failed call.
     */
    boolean isAvailable();

    ProviderCapabilities capabilities();
}
