package com.ibm.consulting.sim.shared.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.consulting.sim.shared.domain.RateLimiterService;
import com.ibm.consulting.sim.shared.infrastructure.cache.UpstashRestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@ConditionalOnProperty(
        name = "app.cache.provider",
        havingValue = "upstash"
)
public class UpstashRateLimiterService
        implements RateLimiterService {

    private final UpstashRestClient client;
    private static final Logger log =
            LoggerFactory.getLogger(UpstashRateLimiterService.class);

    public UpstashRateLimiterService(
            UpstashRestClient client
    ) {
        this.client = client;
    }

    @Override
    public boolean tryAcquire(
            String key,
            int maxRequests,
            Duration window
    ) {

        try {
            String redisKey = "rate-limit::" + key;

            String script = """
                local current = redis.call('INCR', KEYS[1])

                if current == 1 then
                    redis.call('EXPIRE', KEYS[1], ARGV[1])
                end

                return current
                """;

            JsonNode result =
                    client.execute(
                            List.of(
                                    "EVAL",
                                    script,
                                    "1",
                                    redisKey,
                                    String.valueOf(window.toSeconds())
                            )
                    );

            long requestCount = result.asLong();

            return requestCount <= maxRequests;

        } catch (Exception e) {

            log.warn(
                    "Upstash rate limiter unavailable, allowing request: {}",
                    e.getMessage()
            );

            return true;
        }
    }
}