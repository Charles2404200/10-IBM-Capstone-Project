package com.ibm.consulting.sim.identity.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.consulting.sim.identity.application.CaffeineLoginAttemptStore;
import com.ibm.consulting.sim.identity.application.LoginAttemptProperties;
import com.ibm.consulting.sim.identity.application.LoginAttemptStore;
import com.ibm.consulting.sim.shared.infrastructure.cache.UpstashRestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Atomic, cross-instance login-failure counters using the configured Redis REST client. */
@Component
@ConditionalOnProperty(name = "app.cache.provider", havingValue = "upstash")
public class RedisLoginAttemptStore implements LoginAttemptStore {

    private static final Logger log = LoggerFactory.getLogger(RedisLoginAttemptStore.class);
    private static final String KEY_PREFIX = "identity:login-failures:";
    private static final String ACQUIRE_SCRIPT = """
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local maximum = tonumber(ARGV[1])
            if current >= maximum then
              return -current
            end
            current = current + 1
            redis.call('SET', KEYS[1], tostring(current), 'PX', ARGV[2])
            return current
            """;
    private static final String RELEASE_SCRIPT = """
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            if current <= 1 then
              redis.call('DEL', KEYS[1])
              return 0
            end
            return redis.call('DECR', KEYS[1])
            """;

    private final UpstashRestClient client;
    private final LoginAttemptProperties properties;
    private final CaffeineLoginAttemptStore fallback;

    public RedisLoginAttemptStore(UpstashRestClient client, LoginAttemptProperties properties) {
        this(client, properties, new CaffeineLoginAttemptStore(properties));
    }

    RedisLoginAttemptStore(UpstashRestClient client, LoginAttemptProperties properties,
                           CaffeineLoginAttemptStore fallback) {
        this.client = client;
        this.properties = properties;
        this.fallback = fallback;
    }

    @Override
    public boolean tryAcquire(String key) {
        try {
            JsonNode result = client.execute(List.of(
                    "EVAL", ACQUIRE_SCRIPT, "1", redisKey(key),
                    Integer.toString(properties.getMaxFailures()),
                    Long.toString(properties.getWindow().toMillis())));
            if (result == null || !result.isIntegralNumber()) {
                throw new IllegalStateException("Redis login admission returned no integer result");
            }
            int signedCount = result.intValue();
            fallback.synchronizeAtLeast(key, Math.abs(signedCount), signedCount > 0);
            return signedCount > 0;
        } catch (RuntimeException exception) {
            log.warn("Distributed login admission failed; using local protection: {}", exception.getMessage());
            return fallback.tryAcquire(key);
        }
    }

    @Override
    public void release(String key) {
        fallback.release(key);
        try {
            client.execute(List.of("EVAL", RELEASE_SCRIPT, "1", redisKey(key)));
        } catch (RuntimeException exception) {
            log.warn("Distributed login reservation release failed: {}", exception.getMessage());
        }
    }

    @Override
    public int failureCount(String key) {
        try {
            JsonNode result = client.execute(List.of("GET", redisKey(key)));
            return result == null ? 0 : result.asInt(0);
        } catch (RuntimeException exception) {
            log.warn("Distributed login-attempt lookup failed; using local protection: {}", exception.getMessage());
            return fallback.failureCount(key);
        }
    }

    @Override
    public void reset(String key) {
        fallback.reset(key);
        try {
            client.execute(List.of("DEL", redisKey(key)));
        } catch (RuntimeException exception) {
            log.warn("Distributed login-attempt reset failed: {}", exception.getMessage());
        }
    }

    private String redisKey(String key) {
        return KEY_PREFIX + key;
    }
}
