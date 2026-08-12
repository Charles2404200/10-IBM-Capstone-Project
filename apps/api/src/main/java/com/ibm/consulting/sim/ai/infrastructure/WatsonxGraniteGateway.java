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
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * IBM watsonx.ai Granite provider — the platform's premium/paid-tier model,
 * deliberately given a real job (not just a branding requirement): it is the
 * highest-priority candidate for {@link AiTaskType#ASSESSMENT} (end-of-engagement
 * coaching feedback, where 2-5s latency is a non-issue and reasoning quality
 * matters most) and a strong fallback for every other task. See design doc
 * "watsonx.ai's real role" section.
 *
 * <p>Enabled whenever the AI orchestration layer is not in mock mode
 * ({@code app.ai.mock-mode=false}) — independent of {@code app.watsonx.mock-mode},
 * which continues to gate only the (unrelated) RAG embeddings gateway.
 * {@link #isAvailable()} reports {@code false} until real credentials are
 * configured, so the router silently skips watsonx (falling through to the free
 * providers) until an operator adds a real API key — the "leave room for later"
 * requirement — with zero code changes needed at that point.
 */
@Component
@ConditionalOnProperty(name = "app.ai.mock-mode", havingValue = "false")
public class WatsonxGraniteGateway implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(WatsonxGraniteGateway.class);
    private static final String PROVIDER_ID = "watsonx-granite";
    private static final String GENERATION_PATH = "/ml/v1/text/generation?version=2024-05-01";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);
    private static final ProviderCapabilities CAPABILITIES = new ProviderCapabilities(
            Set.of(AiTaskType.ASSESSMENT, AiTaskType.PROPOSAL_REVIEW, AiTaskType.CONVERSATION, AiTaskType.CLASSIFICATION,
                    AiTaskType.CLIENT_INTELLIGENCE, AiTaskType.EVIDENCE_EXTRACTION),
            LatencyTier.MEDIUM,
            ReasoningTier.HIGH,
            true);

    private final WebClient inferenceClient;
    private final IamTokenProvider tokenProvider;
    private final String apiKey;
    private final String projectId;
    private final String modelId;

    public WatsonxGraniteGateway(
            @Value("${app.watsonx.base-url}") String baseUrl,
            @Value("${app.watsonx.api-key}") String apiKey,
            @Value("${app.watsonx.project-id}") String projectId,
            @Value("${app.watsonx.model-id}") String modelId,
            IamTokenProvider tokenProvider) {
        this.inferenceClient = WebClient.builder().baseUrl(baseUrl).build();
        this.tokenProvider = tokenProvider;
        this.apiKey = apiKey;
        this.projectId = projectId;
        this.modelId = modelId;
    }

    @Override
    public String id() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isAvailable() {
        return isConfigured(apiKey) && isConfigured(projectId);
    }

    private static boolean isConfigured(String value) {
        return value != null && !value.isBlank() && !"mock".equalsIgnoreCase(value);
    }

    @Override
    public ProviderCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public String complete(String useCase, String prompt) {
        String bearerToken;
        try {
            bearerToken = tokenProvider.resolveAccessToken();
        } catch (Exception e) {
            throw new AiProviderException("Failed to obtain watsonx IAM token for use-case " + useCase, e);
        }

        Map<String, Object> body = Map.of(
                "input", prompt,
                "model_id", modelId,
                "project_id", projectId,
                "parameters", Map.of(
                        "decoding_method", "greedy",
                        "max_new_tokens", 500,
                        "repetition_penalty", 1.1));

        try {
            JsonNode response = inferenceClient.post()
                    .uri(GENERATION_PATH)
                    .header("Authorization", "Bearer " + bearerToken)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(REQUEST_TIMEOUT)
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(500))
                            .doBeforeRetry(signal -> log.warn("Retrying watsonx call for use-case {} (attempt {})",
                                    useCase, signal.totalRetries() + 1)))
                    .block();

            if (response == null || !response.has("results") || response.get("results").isEmpty()) {
                throw new AiProviderException("watsonx.ai returned no generation results for use-case " + useCase);
            }
            return response.get("results").get(0).path("generated_text").asText("");
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            log.warn("watsonx call failed for use-case {}: {}", useCase, e.getMessage());
            throw new AiProviderException("watsonx call failed for use-case " + useCase, e);
        }
    }
}
