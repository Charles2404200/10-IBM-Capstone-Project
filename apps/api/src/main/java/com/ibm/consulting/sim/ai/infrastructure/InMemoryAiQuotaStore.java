package com.ibm.consulting.sim.ai.infrastructure;

import com.ibm.consulting.sim.ai.application.AiQuotaStore;
import com.ibm.consulting.sim.shared.infrastructure.cache.UpstashRestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Single-instance, in-memory fallback {@link AiQuotaStore}. Used when no distributed
 * cache is configured (local dev with {@code app.cache.provider=caffeine}). The daily
 * bucket is encoded into the map key itself, so counters "reset" naturally at UTC
 * midnight without a scheduled job — yesterday's key is simply never read again.
 *
 * <p>Condition is on the absence of {@link UpstashRestClient} (the dependency the
 * distributed alternative needs), not on this class's own bean — avoids relying on
 * bean-creation ordering between the two {@code AiQuotaStore} implementations.
 */
@Component
@ConditionalOnMissingBean(UpstashRestClient.class)
public class InMemoryAiQuotaStore implements AiQuotaStore {

    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public boolean tryConsume(String providerId, long dailyLimit) {
        if (dailyLimit <= 0) {
            return true;
        }
        long usage = counters.computeIfAbsent(dailyKey(providerId), k -> new AtomicLong()).incrementAndGet();
        return usage <= dailyLimit;
    }

    @Override
    public long currentUsage(String providerId) {
        AtomicLong counter = counters.get(dailyKey(providerId));
        return counter == null ? 0 : counter.get();
    }

    private static String dailyKey(String providerId) {
        return providerId + ":" + LocalDate.now(ZoneOffset.UTC);
    }
}
