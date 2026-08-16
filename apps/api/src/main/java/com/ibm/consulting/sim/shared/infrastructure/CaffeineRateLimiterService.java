package com.ibm.consulting.sim.shared.infrastructure;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ibm.consulting.sim.shared.domain.LocalCacheFailurePolicy;
import com.ibm.consulting.sim.shared.domain.RateLimiterService;
import com.ibm.consulting.sim.shared.domain.RateLimiterUnavailableException;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(
        name = "app.cache.provider",
        havingValue = "caffeine",
        matchIfMissing = true
)
public class CaffeineRateLimiterService
        implements RateLimiterService<LocalCacheFailurePolicy> {

    private final Cache<String, RateLimitEntry> cache;
    private static final Logger log =
            LoggerFactory.getLogger(UpstashRateLimiterService.class);

    public CaffeineRateLimiterService() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterAccess(1, TimeUnit.HOURS)
                .build();
    }

    @Override
    public boolean tryAcquire(
            String key,
            int maxRequests,
            Duration window,
            LocalCacheFailurePolicy failurePolicy
    ) {

        try {

            String cacheKey = "rate-limit::" + key;

            long now = System.currentTimeMillis();

            RateLimitEntry entry =
                    cache.asMap().compute(
                            cacheKey,
                            (k, existing) -> {

                                if (existing == null
                                        || now >= existing.expiresAt()) {

                                    return new RateLimitEntry(
                                            1,
                                            now + window.toMillis()
                                    );
                                }

                                return new RateLimitEntry(
                                        existing.count() + 1,
                                        existing.expiresAt()
                                );
                            }
                    );

            return entry.count() <= maxRequests;

        } catch (Exception e) {

            log.error(
                    "Caffeine rate limiter failed for key={}, policy={}",
                    key,
                    failurePolicy,
                    e
            );

            return switch (failurePolicy) {

                case FAIL_OPEN -> {
                    log.warn(
                            "Caffeine limiter unavailable. FAIL_OPEN applied for key={}",
                            key
                    );

                    yield true;
                }

                case FAIL_CLOSED -> throw
                        new RateLimiterUnavailableException();
            };
        }
    }

    private record RateLimitEntry(
            int count,
            long expiresAt
    ) {
    }
}