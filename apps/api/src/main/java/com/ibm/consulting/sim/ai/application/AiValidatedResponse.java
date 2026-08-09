package com.ibm.consulting.sim.ai.application;

/** A schema-valid AI result plus the provider that produced it. */
public record AiValidatedResponse<T>(T value, String providerId) {
}
