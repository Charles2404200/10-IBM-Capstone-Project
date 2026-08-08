package com.ibm.consulting.sim.ai.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.consulting.sim.ai.domain.EmbeddingGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * IBM watsonx.ai embeddings gateway, used to vectorise scenario knowledge and
 * meeting queries for the RAG pipeline (§5.5). Enabled when app.watsonx.mock-mode=false.
 */
@Component
@ConditionalOnProperty(name = "app.watsonx.mock-mode", havingValue = "false")
public class WatsonxEmbeddingGateway implements EmbeddingGateway {

    private static final String EMBEDDINGS_PATH = "/ml/v1/text/embeddings?version=2024-05-01";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);

    private final WebClient client;
    private final IamTokenProvider tokenProvider;
    private final String projectId;
    private final String embeddingModelId;

    public WatsonxEmbeddingGateway(
            @Value("${app.watsonx.base-url}") String baseUrl,
            @Value("${app.watsonx.project-id}") String projectId,
            @Value("${app.watsonx.embedding-model-id}") String embeddingModelId,
            IamTokenProvider tokenProvider) {
        this.client = WebClient.builder().baseUrl(baseUrl).build();
        this.tokenProvider = tokenProvider;
        this.projectId = projectId;
        this.embeddingModelId = embeddingModelId;
    }

    @Override
    public float[] embed(String text) {
        Map<String, Object> body = Map.of(
                "inputs", List.of(text),
                "model_id", embeddingModelId,
                "project_id", projectId);

        JsonNode response = client.post()
                .uri(EMBEDDINGS_PATH)
                .header("Authorization", "Bearer " + tokenProvider.resolveAccessToken())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(REQUEST_TIMEOUT)
                .block();

        if (response == null || !response.has("results") || response.get("results").isEmpty()) {
            throw new IllegalStateException("watsonx.ai returned no embedding results");
        }
        JsonNode embeddingNode = response.get("results").get(0).path("embedding");
        float[] vector = new float[embeddingNode.size()];
        for (int i = 0; i < embeddingNode.size(); i++) {
            vector[i] = (float) embeddingNode.get(i).asDouble();
        }
        return vector;
    }
}
