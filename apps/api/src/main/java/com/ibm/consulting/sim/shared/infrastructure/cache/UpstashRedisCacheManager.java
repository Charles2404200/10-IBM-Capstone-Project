package com.ibm.consulting.sim.shared.infrastructure.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link CacheManager} backed by Upstash Redis (via REST). Unlike the
 * Caffeine manager it replaces, cache regions are created lazily on first
 * use rather than pre-registered, so new {@code @Cacheable(cacheNames=...)}
 * call sites don't require a config change here.
 *
 * <p>This is the swap point for a future on-prem/IBM Redis deployment: once
 * IBM Cloud Databases for Redis (or any standard Redis endpoint) is
 * available, add {@code spring-boot-starter-data-redis}, provide a
 * {@code RedisCacheManager} bean conditional on
 * {@code app.cache.provider=redis}, and no application code outside
 * {@link com.ibm.consulting.sim.shared.config.CacheConfig} needs to change —
 * every {@code @Cacheable}/{@code @CacheEvict} call site depends only on the
 * standard Spring {@link CacheManager}/{@link Cache} SPI, not on Upstash.
 */
public class UpstashRedisCacheManager implements CacheManager {

    private final UpstashRestClient client;
    private final Duration defaultTtl;
    private final Map<String, Duration> ttlOverrides;
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();

    public UpstashRedisCacheManager(UpstashRestClient client, Duration defaultTtl,
                                     Map<String, Duration> ttlOverrides) {
        this.client = client;
        this.defaultTtl = defaultTtl;
        this.ttlOverrides = ttlOverrides;
    }

    @Override
    public Cache getCache(String name) {
        return caches.computeIfAbsent(name,
                n -> new UpstashRedisCache(n, client, ttlOverrides.getOrDefault(n, defaultTtl)));
    }

    @Override
    public Collection<String> getCacheNames() {
        return caches.keySet();
    }
}
