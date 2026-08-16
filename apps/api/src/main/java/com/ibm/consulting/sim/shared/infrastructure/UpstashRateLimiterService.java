package com.ibm.consulting.sim.shared.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.consulting.sim.shared.domain.DistributedCacheFailurePolicy;
import com.ibm.consulting.sim.shared.domain.LocalCacheFailurePolicy;
import com.ibm.consulting.sim.shared.domain.RateLimiterService;
import com.ibm.consulting.sim.shared.domain.RateLimiterUnavailableException;
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
        implements RateLimiterService<DistributedCacheFailurePolicy> {

    private final UpstashRestClient client;
    private final RateLimiterService fallbackRateLimiter;
    private static final Logger log =
            LoggerFactory.getLogger(UpstashRateLimiterService.class);

    public UpstashRateLimiterService(
            UpstashRestClient client,
            RateLimiterService<LocalCacheFailurePolicy> fallbackRateLimiter
    ) {
        this.client = client;
        this.fallbackRateLimiter = fallbackRateLimiter;
    }

    @Override
    public boolean tryAcquire(
            String key,
            int maxRequests,
            Duration window,
            DistributedCacheFailurePolicy failurePolicy
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

            return switch (failurePolicy) {

                case FAIL_OPEN -> true;

                case FAIL_CLOSED -> throw new RateLimiterUnavailableException();

                case LOCAL_FALLBACK -> fallbackRateLimiter.tryAcquire(
                        key,
                        maxRequests,
                        window,
                        LocalCacheFailurePolicy.FAIL_CLOSED
                );
            };

        }
    }
}