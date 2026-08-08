package com.ibm.consulting.sim.ai.domain;

/**
 * Signals an infrastructure-level failure calling an {@link AiProvider} (network error,
 * non-2xx response, timeout, rate limit). Distinct from {@link AiValidationException},
 * which signals a well-formed HTTP response whose *content* failed schema validation.
 * The router uses this distinction to decide "try the next provider" (this exception)
 * versus "repair-retry the same call" (validation failure, handled one layer up in
 * {@link com.ibm.consulting.sim.ai.application.AiOrchestrationService}).
 */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
