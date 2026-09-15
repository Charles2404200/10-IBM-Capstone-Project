package com.ibm.consulting.sim.knowledge.api;

import com.ibm.consulting.sim.knowledge.domain.KnowledgeCollection;
import com.ibm.consulting.sim.knowledge.domain.KnowledgeDocument;
import java.time.Instant;
import java.util.UUID;

public record KnowledgeDocumentSummary(
        UUID id, String title, KnowledgeCollection collection, UUID personaId, Instant createdAt, String sourceText) {

    public static KnowledgeDocumentSummary from(KnowledgeDocument document) {
        return new KnowledgeDocumentSummary(
                document.getId(), document.getTitle(), document.getCollection(),
                document.getPersonaId(), document.getCreatedAt(), document.getSourceText());
    }
}