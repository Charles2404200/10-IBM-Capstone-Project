package com.ibm.consulting.sim.ai.application;

import com.ibm.consulting.sim.ai.domain.AiTaskType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * In-memory rolling counters for the AI Operations admin view (§21/22 of the
 * orchestration design): requests, successes, failures and total latency per
 * provider, plus a fallback counter (how often the first-choice candidate for
 * a task had to be skipped in favour of the next one).
 *
 * <p>Deliberately not persisted — this is a live "what's happening right now"
 * dashboard, not an audit trail (the durable, queryable per-call audit trail
 * already exists as {@code AiTrace} rows in Postgres). Resetting on restart is
 * the correct behaviour for an operational health snapshot.
 */
public class AiOperationsRecorder {

    private final Map<String, ProviderCounters> byProvider = new ConcurrentHashMap<>();

    public void recordSuccess(String providerId, AiTaskType task, long latencyMs, boolean wasFallback) {
        ProviderCounters counters = byProvider.computeIfAbsent(providerId, k -> new ProviderCounters());
        counters.requests.increment();
        counters.successes.increment();
        counters.totalLatencyMs.add(latencyMs);
        if (wasFallback) {
            counters.fallbacks.increment();
        }
    }

    public void recordFailure(String providerId, AiTaskType task, long latencyMs) {
        ProviderCounters counters = byProvider.computeIfAbsent(providerId, k -> new ProviderCounters());
        counters.requests.increment();
        counters.failures.increment();
        counters.totalLatencyMs.add(latencyMs);
    }

    public List<AiProviderStat> snapshot(Map<String, Long> quotaUsageByProvider,
                                          Map<String, Long> quotaLimitByProvider,
                                          Map<String, String> circuitStateByProvider,
                                          Map<String, Boolean> availabilityByProvider) {
        return byProvider.entrySet().stream()
                .map(entry -> {
                    String providerId = entry.getKey();
                    ProviderCounters c = entry.getValue();
                    long requests = c.requests.sum();
                    double avgLatency = requests == 0 ? 0 : (double) c.totalLatencyMs.sum() / requests;
                    double fallbackRate = requests == 0 ? 0 : (double) c.fallbacks.sum() / requests;
                    return new AiProviderStat(
                            providerId,
                            Boolean.TRUE.equals(availabilityByProvider.get(providerId)),
                            circuitStateByProvider.getOrDefault(providerId, "CLOSED"),
                            requests,
                            c.successes.sum(),
                            c.failures.sum(),
                            Math.round(avgLatency),
                            Math.round(fallbackRate * 1000d) / 10d,
                            quotaUsageByProvider.getOrDefault(providerId, 0L),
                            quotaLimitByProvider.getOrDefault(providerId, 0L));
                })
                .sorted((a, b) -> Long.compare(b.requestsToday(), a.requestsToday()))
                .toList();
    }

    private static final class ProviderCounters {
        private final LongAdder requests = new LongAdder();
        private final LongAdder successes = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private final LongAdder fallbacks = new LongAdder();
        private final LongAdder totalLatencyMs = new LongAdder();
    }
}
