package com.ibm.consulting.sim.knowledge.domain;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository {
    DocumentChunk save(DocumentChunk chunk);
    List<DocumentChunk> saveAll(List<DocumentChunk> chunks);

    /** Chunks visible to a given scenario/persona scope for a specific collection. */
    List<DocumentChunk> findByCollectionAndScope(KnowledgeCollection collection, UUID scenarioId, UUID personaId);

    /** Ordered corpus passages for a research lane; no semantic top-K truncation. */
    List<DocumentChunk> findResearchCorpus(KnowledgeCollection collection, UUID scenarioId);
    void deleteByDocumentId(UUID documentId);
}
