package com.ibm.consulting.sim.knowledge.application;

import com.ibm.consulting.sim.ai.domain.EmbeddingGateway;
import com.ibm.consulting.sim.knowledge.domain.DocumentChunkRepository;
import com.ibm.consulting.sim.knowledge.domain.KnowledgeCollection;
import com.ibm.consulting.sim.knowledge.domain.KnowledgeRetrievalPolicy;
import com.ibm.consulting.sim.knowledge.domain.KnowledgeRetrievalPolicy.RankedChunk;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Retrieves the top-K most relevant knowledge chunks for a query, scoped to a
 * scenario/persona and a specific collection (§5.5). Used by
 * {@code PersonaPromptAssembler} to ground meeting turns, and can be reused
 * by future assessment-rubric grounding.
 */
@Service
public class KnowledgeRetrievalService {

    private static final int DEFAULT_TOP_K = 4;
    private static final double MIN_SIMILARITY = 0.05;

    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingGateway embeddingGateway;

    public KnowledgeRetrievalService(DocumentChunkRepository chunkRepository, EmbeddingGateway embeddingGateway) {
        this.chunkRepository = chunkRepository;
        this.embeddingGateway = embeddingGateway;
    }

    @Transactional(readOnly = true)
    public List<String> retrieveRelevantPassages(KnowledgeCollection collection, UUID scenarioId, UUID personaId,
                                                  String query) {
        var candidates = chunkRepository.findByCollectionAndScope(collection, scenarioId, personaId);
        if (candidates.isEmpty()) {
            return List.of();
        }
        float[] queryEmbedding = embeddingGateway.embed(query);
        List<RankedChunk> ranked = KnowledgeRetrievalPolicy.topK(queryEmbedding, candidates, DEFAULT_TOP_K);
        return ranked.stream()
                .filter(r -> r.similarity() >= MIN_SIMILARITY)
                .map(r -> r.chunk().getContent())
                .toList();
    }
}
