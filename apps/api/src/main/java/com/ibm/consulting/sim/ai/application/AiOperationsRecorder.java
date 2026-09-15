package com.ibm.consulting.sim.ai.application;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import com.ibm.consulting.sim.ai.domain.AiTaskType;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

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
    private final MeterRegistry meterRegistry;

    public AiOperationsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordSuccess(String providerId, AiTaskType task, long latencyMs, boolean wasFallback) {
        ProviderCounters counters = byProvider.computeIfAbsent(providerId, k -> new ProviderCounters());
        counters.requests.increment();
        counters.successes.increment();
        counters.totalLatencyMs.add(latencyMs);
        if (wasFallback) {
            counters.fallbacks.increment();
        }
        recordMetrics(providerId, task, "success", latencyMs, wasFallback);
    }

    public void recordFailure(String providerId, AiTaskType task, long latencyMs, boolean wasFallback) {
        ProviderCounters counters = byProvider.computeIfAbsent(providerId, k -> new ProviderCounters());
        counters.requests.increment();
        counters.failures.increment();
        counters.totalLatencyMs.add(latencyMs);
        recordMetrics(providerId, task, "failure", latencyMs, wasFallback);
    }

        private void recordMetrics(String providerId, AiTaskType task, String outcome,
                       long latencyMs, boolean wasFallback) {
        String taskName = task.name().toLowerCase();
        String fallback = Boolean.toString(wasFallback);
        Counter.builder("consulting.ai.provider.requests")
            .description("AI provider attempts")
            .tags("provider", providerId, "task", taskName,
                "outcome", outcome, "fallback", fallback)
            .register(meterRegistry)
            .increment();
        Timer.builder("consulting.ai.provider.latency")
            .description("AI provider attempt latency")
            .tags("provider", providerId, "task", taskName,
                "outcome", outcome, "fallback", fallback)
            .publishPercentileHistogram()
            .register(meterRegistry)
            .record(Duration.ofMillis(latencyMs));
        }

    public List<AiProviderStat> snapshot(Map<String, Long> quotaUsageByProvider,
                                          Map<String, Long> quotaLimitByProvider,
                                          Map<String, String> circuitStateByProvider,
                                          Map<String, Boolean> availabilityByProvider) {
        return availabilityByProvider.keySet().stream()
            .map(providerId -> {
                ProviderCounters c =
                    byProvider.getOrDefault(providerId, new ProviderCounters());
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
