package com.ibm.consulting.sim.knowledge.domain;

import java.util.Comparator;
import java.util.List;

/**
 * Ranks candidate chunks by cosine similarity to a query embedding (§5.5).
 * Pure domain logic — no persistence or Spring dependency — so ranking
 * quality can be unit tested independently of the storage strategy.
 */
public final class KnowledgeRetrievalPolicy {

    private KnowledgeRetrievalPolicy() {}

    public record RankedChunk(DocumentChunk chunk, double similarity) {}

    public static List<RankedChunk> topK(float[] queryEmbedding, List<DocumentChunk> candidates, int k) {
        return candidates.stream()
                .map(c -> new RankedChunk(c, cosineSimilarity(queryEmbedding, c.getEmbedding())))
                .sorted(Comparator.comparingDouble(RankedChunk::similarity).reversed())
                .limit(k)
                .toList();
    }

    static double cosineSimilarity(float[] a, float[] b) {
        int length = Math.min(a.length, b.length);
        if (length == 0) {
            return 0d;
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0d;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
