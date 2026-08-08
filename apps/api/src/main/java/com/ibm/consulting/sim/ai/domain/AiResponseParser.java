package com.ibm.consulting.sim.ai.domain;

/**
 * Strategy for parsing and validating a raw AI JSON payload into a typed,
 * schema-checked response. Implementations must never throw for malformed
 * input — they signal failure via {@link AiValidationException} so the
 * orchestration layer can decide whether to repair-retry or fall back.
 */
public interface AiResponseParser<T> {
    T parse(String rawJson) throws AiValidationException;
}
