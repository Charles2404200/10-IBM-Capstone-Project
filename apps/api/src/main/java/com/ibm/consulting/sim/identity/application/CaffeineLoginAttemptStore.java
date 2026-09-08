package com.ibm.consulting.sim.identity.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Bounded single-instance store used when the application cache is local. */
@Component
@ConditionalOnProperty(name = "app.cache.provider", havingValue = "caffeine", matchIfMissing = true)
public class CaffeineLoginAttemptStore implements LoginAttemptStore {

    private final int maximumFailures;
    private final Cache<String, Integer> failures;

    @Autowired
    public CaffeineLoginAttemptStore(LoginAttemptProperties properties) {
        this(properties, Ticker.systemTicker());
    }

    CaffeineLoginAttemptStore(LoginAttemptProperties properties, Ticker ticker) {
        this.maximumFailures = properties.getMaxFailures();
        this.failures = Caffeine.newBuilder()
                .maximumSize(properties.getMaximumTrackedAccounts())
                .expireAfterWrite(properties.getWindow())
                .ticker(ticker)
                .build();
    }

    @Override
    public int failureCount(String key) {
        Integer count = failures.getIfPresent(key);
        return count == null ? 0 : count;
    }

    @Override
    public void recordFailure(String key) {
        failures.asMap().compute(key, (ignored, attempts) -> attempts == null
                ? 1
                : Math.min(maximumFailures, attempts + 1));
    }

    @Override
    public void reset(String key) {
        failures.invalidate(key);
    }
}
