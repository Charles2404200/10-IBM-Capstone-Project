package com.ibm.consulting.sim.identity.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded single-instance store used when the application cache is local. */
@Component
@ConditionalOnProperty(name = "app.cache.provider", havingValue = "caffeine", matchIfMissing = true)
public class CaffeineLoginAttemptStore implements LoginAttemptStore {

    private final int maximumFailures;
    private final long windowNanos;
    private final Ticker ticker;
    private final Cache<String, AttemptWindow> failures;

    @Autowired
    public CaffeineLoginAttemptStore(LoginAttemptProperties properties) {
        this(properties, Ticker.systemTicker());
    }

    CaffeineLoginAttemptStore(LoginAttemptProperties properties, Ticker ticker) {
        this.maximumFailures = properties.getMaxFailures();
        this.windowNanos = properties.getWindow().toNanos();
        this.ticker = ticker;
        this.failures = Caffeine.newBuilder()
                .maximumSize(properties.getMaximumTrackedAccounts())
                .expireAfterWrite(properties.getWindow())
                .ticker(ticker)
                .build();
    }

    @Override
    public boolean tryAcquire(String key) {
        long now = ticker.read();
        AtomicBoolean admitted = new AtomicBoolean();
        failures.asMap().compute(key, (ignored, current) -> {
            if (current == null || current.expiredAt(now)) {
                admitted.set(true);
                return new AttemptWindow(1, deadlineFrom(now));
            }
            if (current.count() >= maximumFailures) {
                return current;
            }
            admitted.set(true);
            return new AttemptWindow(current.count() + 1, deadlineFrom(now));
        });
        return admitted.get();
    }

    @Override
    public void release(String key) {
        long now = ticker.read();
        failures.asMap().computeIfPresent(key, (ignored, current) -> {
            if (current.expiredAt(now) || current.count() <= 1) {
                return null;
            }
            return new AttemptWindow(current.count() - 1, current.expiresAtNanos());
        });
    }

    @Override
    public int failureCount(String key) {
        AttemptWindow current = failures.getIfPresent(key);
        if (current == null) {
            return 0;
        }
        if (current.expiredAt(ticker.read())) {
            failures.asMap().remove(key, current);
            return 0;
        }
        return current.count();
    }

    /** Mirrors an authoritative distributed count for outage-safe failover. */
    public void synchronizeAtLeast(String key, int count, boolean refreshExpiry) {
        if (count <= 0) {
            return;
        }
        long now = ticker.read();
        failures.asMap().compute(key, (ignored, current) -> {
            boolean missingOrExpired = current == null || current.expiredAt(now);
            int localCount = missingOrExpired ? 0 : current.count();
            long deadline = refreshExpiry || missingOrExpired
                    ? deadlineFrom(now)
                    : current.expiresAtNanos();
            return new AttemptWindow(Math.min(maximumFailures, Math.max(localCount, count)), deadline);
        });
    }

    @Override
    public void reset(String key) {
        failures.invalidate(key);
    }

    private long deadlineFrom(long now) {
        return Math.addExact(now, windowNanos);
    }

    private record AttemptWindow(int count, long expiresAtNanos) {
        boolean expiredAt(long now) {
            return now >= expiresAtNanos;
        }
    }
}
