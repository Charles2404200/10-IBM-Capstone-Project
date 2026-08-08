package com.ibm.consulting.sim.ai.infrastructure;

import com.ibm.consulting.sim.ai.domain.EmbeddingGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Deterministic hashing-based embedding for local development and tests.
 * Not semantically meaningful, but stable and dependency-free — good enough
 * to exercise the RAG pipeline's chunking, storage and ranking logic without
 * calling watsonx. Enabled when app.watsonx.mock-mode=true (default).
 */
@Component
@ConditionalOnProperty(name = "app.watsonx.mock-mode", havingValue = "true", matchIfMissing = true)
public class MockEmbeddingGateway implements EmbeddingGateway {

    private static final int DIMENSIONS = 128;

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text == null || text.isBlank()) {
            return vector;
        }
        String normalised = text.toLowerCase(Locale.ROOT);
        for (String token : normalised.split("\\W+")) {
            if (token.isBlank()) {
                continue;
            }
            int bucket = Math.floorMod(stableHash(token), DIMENSIONS);
            vector[bucket] += 1f;
        }
        normalise(vector);
        return vector;
    }

    private int stableHash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            int result = 0;
            for (int i = 0; i < 4; i++) {
                result = (result << 8) | (hash[i] & 0xFF);
            }
            return result;
        } catch (Exception e) {
            return token.hashCode();
        }
    }

    private void normalise(float[] vector) {
        double sumSquares = 0;
        for (float v : vector) {
            sumSquares += v * v;
        }
        if (sumSquares == 0) {
            return;
        }
        double norm = Math.sqrt(sumSquares);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
    }
}
