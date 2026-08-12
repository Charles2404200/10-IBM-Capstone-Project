package com.ibm.consulting.sim.ai.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.consulting.sim.ai.domain.AiProvider;
import com.ibm.consulting.sim.ai.domain.AiProviderException;
import com.ibm.consulting.sim.ai.domain.AiTaskType;
import com.ibm.consulting.sim.ai.domain.LatencyTier;
import com.ibm.consulting.sim.ai.domain.ProviderCapabilities;
import com.ibm.consulting.sim.ai.domain.ReasoningTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenRouter free-tier provider — emergency/dev fallback candidate (§20 of the design
 * doc: OpenRouter's free plan is ~50 requests/day, so it is never configured as the
 * primary route for any task, only as the last-priority candidate). Uses the OpenAI-
 * compatible {@code /chat/completions} endpoint, defaulting to the free
 * {@code openai/gpt-oss-20b:free} model.
 */
@Component
@ConditionalOnProperty(name = "app.ai.mock-mode", havingValue = "false")
public class OpenRouterProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterProvider.class);
    private static final String PROVIDER_ID = "openrouter-free";
    private static final ProviderCapabilities CAPABILITIES = new ProviderCapabilities(
            Set.of(AiTaskType.CONVERSATION, AiTaskType.CLASSIFICATION, AiTaskType.CLIENT_INTELLIGENCE, AiTaskType.PROPOSAL_REVIEW,
                    AiTaskType.EVIDENCE_EXTRACTION, AiTaskType.ASSESSMENT),
            LatencyTier.VARIABLE,
            ReasoningTier.MEDIUM,
            true);

    private final RestClient restClient;
    private final String apiKey;
    private final String modelId;

    public OpenRouterProvider(
            @Value("${app.ai.providers.openrouter.base-url:https://openrouter.ai/api/v1}") String baseUrl,
            @Value("${app.ai.providers.openrouter.api-key:}") String apiKey,
            @Value("${app.ai.providers.openrouter.model-id:openai/gpt-oss-20b:free}") String modelId,
            @Value("${app.ai.providers.openrouter.timeout-ms:12000}") int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.apiKey = apiKey;
        this.modelId = modelId;
    }

    @Override
    public String id() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public ProviderCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public String complete(String useCase, String prompt) {
        // Many free OpenRouter models (including gpt-oss) emit a long internal
        // "reasoning" trace before the actual answer by default, which is pure added
        // latency for live/interactive use cases where the Simulation Engine (not the
        // model) enforces game-state correctness. Keep full reasoning effort only for
        // the latency-tolerant ASSESSMENT task.
        boolean lowLatency = AiTaskType.fromUseCase(useCase) != AiTaskType.ASSESSMENT;
        Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "model", modelId,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.4,
                "max_tokens", 800,
                "response_format", Map.of("type", "json_object")));
        if (lowLatency) {
            body.put("reasoning", Map.of("effort", "low", "exclude", true));
        }
        try {
            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.has("choices") || response.get("choices").isEmpty()) {
                throw new AiProviderException("OpenRouter returned no choices for use-case " + useCase);
            }
            return response.get("choices").get(0).path("message").path("content").asText("");
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OpenRouter call failed for use-case {}: {}", useCase, e.getMessage());
            throw new AiProviderException("OpenRouter call failed for use-case " + useCase, e);
        }
    }
}
