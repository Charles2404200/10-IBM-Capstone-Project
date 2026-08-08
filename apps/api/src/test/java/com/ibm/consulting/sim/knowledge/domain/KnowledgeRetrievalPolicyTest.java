package com.ibm.consulting.sim.knowledge.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeRetrievalPolicyTest {

    @Test
    void cosineSimilarityOfIdenticalVectorsIsOne() {
        float[] a = {1f, 0f, 0f};
        assertThat(KnowledgeRetrievalPolicy.cosineSimilarity(a, a)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void cosineSimilarityOfOrthogonalVectorsIsZero() {
        float[] a = {1f, 0f};
        float[] b = {0f, 1f};
        assertThat(KnowledgeRetrievalPolicy.cosineSimilarity(a, b)).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void cosineSimilarityHandlesZeroVectorWithoutError() {
        float[] zero = {0f, 0f, 0f};
        float[] other = {1f, 2f, 3f};
        assertThat(KnowledgeRetrievalPolicy.cosineSimilarity(zero, other)).isZero();
    }

    @Test
    void topKReturnsMostSimilarChunksInDescendingOrder() {
        UUID docId = UUID.randomUUID();
        UUID scenarioId = UUID.randomUUID();

        DocumentChunk exactMatch = DocumentChunk.create(docId, scenarioId, null,
                KnowledgeCollection.SCENARIO_TRUTH, 0, "exact", new float[]{1f, 0f, 0f});
        DocumentChunk partialMatch = DocumentChunk.create(docId, scenarioId, null,
                KnowledgeCollection.SCENARIO_TRUTH, 1, "partial", new float[]{0.5f, 0.5f, 0f});
        DocumentChunk noMatch = DocumentChunk.create(docId, scenarioId, null,
                KnowledgeCollection.SCENARIO_TRUTH, 2, "none", new float[]{0f, 0f, 1f});

        List<KnowledgeRetrievalPolicy.RankedChunk> ranked = KnowledgeRetrievalPolicy.topK(
                new float[]{1f, 0f, 0f}, List.of(noMatch, partialMatch, exactMatch), 2);

        assertThat(ranked).hasSize(2);
        assertThat(ranked.get(0).chunk().getContent()).isEqualTo("exact");
        assertThat(ranked.get(1).chunk().getContent()).isEqualTo("partial");
    }

    @Test
    void topKRespectsLimit() {
        UUID docId = UUID.randomUUID();
        List<DocumentChunk> chunks = List.of(
                DocumentChunk.create(docId, null, null, KnowledgeCollection.CONSULTING_PRACTICE, 0, "a", new float[]{1f}),
                DocumentChunk.create(docId, null, null, KnowledgeCollection.CONSULTING_PRACTICE, 1, "b", new float[]{1f}),
                DocumentChunk.create(docId, null, null, KnowledgeCollection.CONSULTING_PRACTICE, 2, "c", new float[]{1f}));

        List<KnowledgeRetrievalPolicy.RankedChunk> ranked = KnowledgeRetrievalPolicy.topK(new float[]{1f}, chunks, 1);
        assertThat(ranked).hasSize(1);
    }
}
