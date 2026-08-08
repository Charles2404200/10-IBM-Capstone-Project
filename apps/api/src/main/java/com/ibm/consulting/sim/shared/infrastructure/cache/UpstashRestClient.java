package com.ibm.consulting.sim.shared.infrastructure.cache;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Thin HTTP client for the Upstash Redis REST API
 * (https://upstash.com/docs/redis/features/restapi). Upstash is used here
 * instead of a raw TCP Redis client (Lettuce/Jedis) because it requires no
 * persistent connection or driver — a plain HTTPS POST per command — which
 * suits a containerised Spring app without extra networking setup and works
 * identically from any environment that can reach the internet over HTTPS.
 *
 * <p>Command execution goes through Upstash's generic command endpoint: a
 * JSON array of command tokens is POSTed to the database's REST URL and the
 * response is {@code {"result": ...}} (or {@code {"error": ...}} on failure).
 * This one method is enough to implement the full {@link org.springframework.cache.Cache}
 * SPI (GET/SET/DEL/SCAN) used by {@link UpstashRedisCache}.
 */
@Component
@ConditionalOnProperty(name = "app.cache.provider", havingValue = "upstash")
public class UpstashRestClient {

    private static final Logger log = LoggerFactory.getLogger(UpstashRestClient.class);

    private final RestClient restClient;

    public UpstashRestClient(
            @Value("${app.cache.upstash.rest-url}") String restUrl,
            @Value("${app.cache.upstash.rest-token}") String restToken) {
        this.restClient = RestClient.builder()
                .baseUrl(restUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + restToken)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Executes a single Redis command (e.g. {@code List.of("GET", "my-key")})
     * and returns the raw {@code result} node, or {@code null} if the key/value
     * is absent. Throws {@link IllegalStateException} if Upstash reports an error.
     */
    public JsonNode execute(List<String> command) {
        try {
            JsonNode response = restClient.post()
                    .body(command)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                return null;
            }
            if (response.hasNonNull("error")) {
                throw new IllegalStateException("Upstash command failed: " + response.get("error").asText());
            }
            JsonNode result = response.get("result");
            return (result == null || result.isNull()) ? null : result;
        } catch (Exception e) {
            log.warn("Upstash REST call failed for command {}: {}", command.isEmpty() ? "?" : command.get(0),
                    e.getMessage());
            throw e;
        }
    }
}
