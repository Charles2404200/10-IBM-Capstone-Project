package com.ibm.consulting.sim.ai.application;

/**
 * Tracks daily request consumption per free-tier AI provider so the router can skip a
 * provider before making a call that would just be rejected (HTTP 429), rather than
 * discovering exhaustion via a failed request. One counter per provider per UTC day.
 *
 * <p>Two implementations exist, following the same swappable-infrastructure pattern as
 * {@link com.ibm.consulting.sim.shared.infrastructure.cache.UpstashRedisCache}: a
 * distributed store (reuses the already-wired Upstash Redis REST client, so quota state
 * is shared across instances/restarts) and an in-memory fallback (single-instance dev,
 * or when no distributed cache is configured). Callers only depend on this interface.
 */
public interface AiQuotaStore {

    /**
     * Atomically increments today's usage counter for {@code providerId} and returns
     * whether the call should proceed, i.e. whether the counter (after incrementing)
     * is still within {@code dailyLimit}. A {@code dailyLimit <= 0} is treated as
     * "unlimited" (always allowed) — used for the premium watsonx tier which isn't a
     * free-quota-constrained provider.
     */
    boolean tryConsume(String providerId, long dailyLimit);

    /** Current usage count for {@code providerId} today, for observability — does not mutate state. */
    long currentUsage(String providerId);
}
