package com.ibm.consulting.sim.shared.infrastructure.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A single named Redis-backed cache region (Spring's {@code cacheNames}
 * concept), stored via the Upstash REST API. Keys are namespaced as
 * {@code "<cacheName>::<key>"} so multiple {@code @Cacheable} regions can
 * share one Redis database without collisions.
 *
 * <p>Values are wrapped in a small {@link Envelope} POJO before JSON
 * serialization. Jackson's default typing reliably resolves the concrete
 * runtime type of a field <em>declared</em> as {@code Object} — but, notably,
 * does <em>not</em> reliably add type metadata to a bare top-level
 * {@code List}/{@code Map} value (a well-known Jackson default-typing
 * limitation for container types). Since almost every {@code @Cacheable}
 * method here returns a {@code List<...>} DTO, wrapping avoids depending on
 * that edge case: {@code Envelope.value} is always declared as {@code Object},
 * so both {@link #get(Object)} and {@link #get(Object, Class)} work for any
 * cached DTO without per-cache configuration.
 */
public class UpstashRedisCache implements Cache {

    private static final Logger log = LoggerFactory.getLogger(UpstashRedisCache.class);

    private final String name;
    private final UpstashRestClient client;
    private final Duration ttl;
    private final ObjectMapper valueMapper;

    public UpstashRedisCache(String name, UpstashRestClient client, Duration ttl) {
        this.name = name;
        this.client = client;
        this.ttl = ttl;
        this.valueMapper = new ObjectMapper();
        this.valueMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.WRAPPER_ARRAY);
    }

    /** Declaring {@code value} as {@code Object} is what makes default typing reliable — see class javadoc. */
    static final class Envelope {
        public Object value;

        public Envelope() {}

        public Envelope(Object value) { this.value = value; }
    }

    @Override
    public String getName() { return name; }

    @Override
    public Object getNativeCache() { return client; }

    @Override
    public ValueWrapper get(Object key) {
        JsonNode result = safeExecute(List.of("GET", redisKey(key)));
        if (result == null) return null;
        return new SimpleValueWrapper(deserialize(result.asText()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        JsonNode result = safeExecute(List.of("GET", redisKey(key)));
        if (result == null) return null;
        Object value = deserialize(result.asText());
        return type == null || type.isInstance(value) ? (T) value : null;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper existing = get(key);
        if (existing != null) {
            @SuppressWarnings("unchecked")
            T value = (T) existing.get();
            return value;
        }
        try {
            T value = valueLoader.call();
            put(key, value);
            return value;
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    @Override
    public void put(Object key, Object value) {
        if (value == null) {
            evict(key);
            return;
        }
        String json = serialize(value);
        safeExecute(List.of("SET", redisKey(key), json, "EX", String.valueOf(ttl.toSeconds())));
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        ValueWrapper existing = get(key);
        if (existing != null) {
            return existing;
        }
        put(key, value);
        return null;
    }

    @Override
    public void evict(Object key) {
        safeExecute(List.of("DEL", redisKey(key)));
    }

    @Override
    public void clear() {
        // Bounded SCAN loop over this region's keys only — acceptable for the
        // low-frequency admin mutations (publish/archive scenario) that trigger
        // allEntries=true evictions in this codebase, not a per-request path.
        String cursor = "0";
        int iterations = 0;
        do {
            JsonNode scanResult = safeExecute(List.of("SCAN", cursor, "MATCH", name + "::*", "COUNT", "200"));
            if (scanResult == null || scanResult.size() < 2) break;
            cursor = scanResult.get(0).asText("0");
            JsonNode keys = scanResult.get(1);
            if (keys != null && keys.isArray() && !keys.isEmpty()) {
                List<String> command = new ArrayList<>();
                command.add("DEL");
                keys.forEach(k -> command.add(k.asText()));
                safeExecute(command);
            }
        } while (!"0".equals(cursor) && ++iterations < 1000);
    }

    private String redisKey(Object key) {
        return name + "::" + key;
    }

    private String serialize(Object value) {
        try {
            return valueMapper.writeValueAsString(new Envelope(value));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize value for cache " + name, e);
        }
    }

    private Object deserialize(String json) {
        try {
            return valueMapper.readValue(json, Envelope.class).value;
        } catch (Exception e) {
            log.warn("Failed to deserialize cached value in region {}, treating as miss: {}", name, e.getMessage());
            return null;
        }
    }

    /** Cache reads/writes must never take the request down if Upstash has a hiccup. */
    private JsonNode safeExecute(List<String> command) {
        try {
            return client.execute(command);
        } catch (Exception e) {
            log.warn("Upstash cache operation failed for region {}, falling back to cache miss: {}",
                    name, e.getMessage());
            return null;
        }
    }
}
