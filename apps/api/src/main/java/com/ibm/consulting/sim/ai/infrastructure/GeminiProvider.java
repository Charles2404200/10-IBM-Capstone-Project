package com.ibm.consulting.sim.ai.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.consulting.sim.ai.domain.AiProvider;
import com.ibm.consulting.sim.ai.domain.AiProviderException;
import com.ibm.consulting.sim.ai.domain.LatencyTier;
import com.ibm.consulting.sim.ai.domain.ProviderCapabilities;
import com.ibm.consulting.sim.ai.domain.AiTaskType;
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
 * Google Gemini free-tier provider — the primary low-latency provider for live,
 * interactive use cases (persona dialogue, outreach replies, cheap classification).
 * Uses Gemini's {@code generateContent} REST endpoint directly (no SDK dependency,
 * consistent with every other outbound integration in this codebase).
 *
 * <p>Configured with {@code responseMimeType: application/json} so the model is
 * constrained to emit JSON at the API level — on top of, not instead of, the
 * orchestration layer's own schema validation and repair-retry.
 */
@Component
@ConditionalOnProperty(name = "app.ai.mock-mode", havingValue = "false")
public class GeminiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);
    private static final String PROVIDER_ID = "gemini-free";
    private static final ProviderCapabilities CAPABILITIES = new ProviderCapabilities(
            Set.of(AiTaskType.CONVERSATION, AiTaskType.CLASSIFICATION, AiTaskType.CLIENT_INTELLIGENCE,
                    AiTaskType.EVIDENCE_EXTRACTION, AiTaskType.ASSESSMENT),
            LatencyTier.LOW,
            ReasoningTier.MEDIUM,
            true);

    private final RestClient restClient;
    private final String apiKey;
    private final String modelId;

    public GeminiProvider(
            @Value("${app.ai.providers.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${app.ai.providers.gemini.api-key:}") String apiKey,
            @Value("${app.ai.providers.gemini.model-id:gemini-2.5-flash}") String modelId,
            @Value("${app.ai.providers.gemini.timeout-ms:8000}") int timeoutMs) {
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
        // Gemini 2.5 Flash defaults to an internal "thinking" pass before answering —
        // fine for the latency-tolerant ASSESSMENT task (reasoning quality matters
        // most there), but for live/interactive use cases (persona dialogue, outreach
        // replies, classification) it turns a sub-second call into a 5-13s one for no
        // real quality gain, since correctness of game state is enforced by the
        // Simulation Engine, not by the model (see AiTaskType javadoc). Disabling it
        // (thinkingBudget=0) measured ~1.2s vs ~5-13s for an identical prompt.
        boolean lowLatency = AiTaskType.fromUseCase(useCase) != AiTaskType.ASSESSMENT;
        Map<String, Object> generationConfig = lowLatency
                ? Map.of(
                        "responseMimeType", "application/json",
                        "temperature", 0.4,
                        "maxOutputTokens", 1024,
                        "thinkingConfig", Map.of("thinkingBudget", 0))
                : Map.of(
                        "responseMimeType", "application/json",
                        "temperature", 0.4,
                        "maxOutputTokens", 1024);
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", generationConfig);
        try {
            JsonNode response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", modelId)
                    .header("x-goog-api-key", apiKey)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.has("candidates") || response.get("candidates").isEmpty()) {
                throw new AiProviderException("Gemini returned no candidates for use-case " + useCase);
            }
            JsonNode parts = response.get("candidates").get(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                throw new AiProviderException("Gemini candidate had no content parts for use-case " + useCase);
            }
            return parts.get(0).path("text").asText("");
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Gemini call failed for use-case {}: {}", useCase, e.getMessage());
            throw new AiProviderException("Gemini call failed for use-case " + useCase, e);
        }
    }
}
