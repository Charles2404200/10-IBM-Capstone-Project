package com.ibm.consulting.sim.knowledge.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

/**
 * A single ingested source document (scenario truth, consulting practice notes,
 * assessment rubric). Scoped by {@code scenarioId}/{@code personaId} where
 * relevant so retrieval never leaks a persona's authorised knowledge into an
 * unrelated scenario (§5.5).
 */
@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocument extends BaseEntity {

    private UUID scenarioId;

    private UUID personaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KnowledgeCollection collection;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text", nullable = false)
    private String sourceText;

    protected KnowledgeDocument() {}

    public static KnowledgeDocument create(UUID scenarioId, UUID personaId, KnowledgeCollection collection,
                                            String title, String sourceText) {
        KnowledgeDocument d = new KnowledgeDocument();
        d.scenarioId = scenarioId;
        d.personaId = personaId;
        d.collection = collection;
        d.title = title;
        d.sourceText = sourceText;
        return d;
    }

    public UUID getScenarioId() { return scenarioId; }
    public UUID getPersonaId() { return personaId; }
    public KnowledgeCollection getCollection() { return collection; }
    public String getTitle() { return title; }
    public String getSourceText() { return sourceText; }
}
