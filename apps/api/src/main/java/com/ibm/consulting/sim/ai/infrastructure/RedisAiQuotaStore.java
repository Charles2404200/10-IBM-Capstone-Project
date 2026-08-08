package com.ibm.consulting.sim.ai.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.consulting.sim.ai.application.AiQuotaStore;
import com.ibm.consulting.sim.shared.infrastructure.cache.UpstashRestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Distributed {@link AiQuotaStore} backed by the same Upstash Redis REST client already
 * wired for the application cache ({@code app.cache.provider=upstash}). Reusing it here
 * (rather than introducing a second Redis client) means AI quota counters survive
 * container restarts and are shared across instances, at zero extra infrastructure cost.
 *
 * <p>Enabled only when {@link UpstashRestClient} exists as a bean — i.e. only when the
 * distributed cache provider is active. Falls back to {@link InMemoryAiQuotaStore}
 * otherwise (see that class for the condition).
 */
@Component
@ConditionalOnBean(UpstashRestClient.class)
public class RedisAiQuotaStore implements AiQuotaStore {

    private static final Logger log = LoggerFactory.getLogger(RedisAiQuotaStore.class);

    /** A little over 24h so a slow-starting day doesn't get cut off by clock skew. */
    private static final long KEY_TTL_SECONDS = 90_000;

    private final UpstashRestClient client;

    public RedisAiQuotaStore(UpstashRestClient client) {
        this.client = client;
    }

    @Override
    public boolean tryConsume(String providerId, long dailyLimit) {
        if (dailyLimit <= 0) {
            return true;
        }
        try {
            String key = dailyKey(providerId);
            JsonNode result = client.execute(List.of("INCR", key));
            long usage = result == null ? 1 : result.asLong(1);
            if (usage == 1) {
                // First increment of the day for this key — attach an expiry so the
                // counter self-resets without needing a scheduled reset job.
                client.execute(List.of("EXPIRE", key, String.valueOf(KEY_TTL_SECONDS)));
            }
            return usage <= dailyLimit;
        } catch (Exception e) {
            log.warn("AI quota check failed for provider {}, allowing the call through: {}", providerId, e.getMessage());
            return true;
        }
    }

    @Override
    public long currentUsage(String providerId) {
        try {
            JsonNode result = client.execute(List.of("GET", dailyKey(providerId)));
            return result == null ? 0 : result.asLong(0);
        } catch (Exception e) {
            log.warn("AI quota lookup failed for provider {}: {}", providerId, e.getMessage());
            return 0;
        }
    }

    private static String dailyKey(String providerId) {
        return "ai:quota:" + providerId + ":" + LocalDate.now(ZoneOffset.UTC);
    }
}
