package com.ibm.consulting.sim.ai.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Shared IBM Cloud IAM bearer-token exchange, used by every watsonx.ai client
 * (text generation, embeddings). Caches the token until shortly before expiry
 * so each request doesn't re-authenticate.
 */
@Component
class IamTokenProvider {

    private static final String IAM_TOKEN_URL = "https://iam.cloud.ibm.com/identity/token";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);
    private static final Duration TOKEN_SAFETY_MARGIN = Duration.ofMinutes(2);

    private final WebClient iamClient = WebClient.builder().baseUrl(IAM_TOKEN_URL).build();
    private final String apiKey;

    private final ReentrantLock tokenLock = new ReentrantLock();
    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    IamTokenProvider(@Value("${app.watsonx.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    String resolveAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }
        tokenLock.lock();
        try {
            if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
                return cachedToken;
            }
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "urn:ibm:params:oauth:grant-type:apikey");
            form.add("apikey", apiKey);

            JsonNode tokenResponse = iamClient.post()
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            if (tokenResponse == null || !tokenResponse.has("access_token")) {
                throw new IllegalStateException("Failed to obtain IBM IAM access token");
            }
            cachedToken = tokenResponse.get("access_token").asText();
            long expiresInSeconds = tokenResponse.path("expires_in").asLong(3600);
            tokenExpiresAt = Instant.now().plusSeconds(expiresInSeconds).minus(TOKEN_SAFETY_MARGIN);
            return cachedToken;
        } finally {
            tokenLock.unlock();
        }
    }
}
