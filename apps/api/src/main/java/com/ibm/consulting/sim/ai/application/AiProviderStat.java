package com.ibm.consulting.sim.ai.application;

/**
 * Snapshot of one provider's live operational health, for the admin AI Operations view.
 * {@code fallbackRatePercent} is how often this provider was used only because a
 * higher-priority candidate for the task was unavailable/exhausted/circuit-open.
 */
public record AiProviderStat(
        String providerId,
        boolean available,
        String circuitState,
        long requestsToday,
        long successCount,
        long failureCount,
        long avgLatencyMs,
        double fallbackRatePercent,
        long quotaUsed,
        long quotaLimit) {
}
