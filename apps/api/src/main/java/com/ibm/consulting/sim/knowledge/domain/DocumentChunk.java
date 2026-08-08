package com.ibm.consulting.sim.knowledge.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

/**
 * A chunk of a {@link KnowledgeDocument} with its embedding vector. Scope fields
 * are denormalised from the parent document so retrieval queries can filter
 * without a join. The embedding is stored as an encoded string (see
 * {@link EmbeddingCodec}) rather than requiring a pgvector extension, keeping
 * the pipeline portable across any relational store while still supporting
 * real cosine-similarity ranking in {@link KnowledgeRetrievalPolicy}.
 */
@Entity
@Table(name = "document_chunks")
public class DocumentChunk extends BaseEntity {

    @Column(nullable = false)
    private UUID documentId;

    private UUID scenarioId;

    private UUID personaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KnowledgeCollection collection;

    @Column(nullable = false)
    private int chunkIndex;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Column(columnDefinition = "text", nullable = false)
    private String embedding;

    protected DocumentChunk() {}

    public static DocumentChunk create(UUID documentId, UUID scenarioId, UUID personaId,
                                        KnowledgeCollection collection, int chunkIndex,
                                        String content, float[] embedding) {
        DocumentChunk c = new DocumentChunk();
        c.documentId = documentId;
        c.scenarioId = scenarioId;
        c.personaId = personaId;
        c.collection = collection;
        c.chunkIndex = chunkIndex;
        c.content = content;
        c.embedding = EmbeddingCodec.encode(embedding);
        return c;
    }

    public UUID getDocumentId() { return documentId; }
    public UUID getScenarioId() { return scenarioId; }
    public UUID getPersonaId() { return personaId; }
    public KnowledgeCollection getCollection() { return collection; }
    public int getChunkIndex() { return chunkIndex; }
    public String getContent() { return content; }
    public float[] getEmbedding() { return EmbeddingCodec.decode(embedding); }
}
