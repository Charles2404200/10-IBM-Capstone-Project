package com.ibm.consulting.sim.knowledge.application;

import com.ibm.consulting.sim.ai.domain.EmbeddingGateway;
import com.ibm.consulting.sim.knowledge.domain.DocumentChunk;
import com.ibm.consulting.sim.knowledge.domain.DocumentChunkRepository;
import com.ibm.consulting.sim.knowledge.domain.KnowledgeCollection;
import com.ibm.consulting.sim.knowledge.domain.KnowledgeDocument;
import com.ibm.consulting.sim.knowledge.domain.KnowledgeDocumentRepository;
import com.ibm.consulting.sim.scenario.domain.Persona;
import com.ibm.consulting.sim.scenario.domain.PersonaRepository;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditAction;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditLogger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Ingests a knowledge source document: chunks it into retrieval-sized passages,
 * embeds each chunk, and persists both the document and its chunks (§5.5, §8 Phase 3).
 */
@Service
public class KnowledgeIngestionService {

    private static final int CHUNK_TARGET_CHARS = 800;
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n\\s*\\n");

    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingGateway embeddingGateway;
    private final PersonaRepository personaRepository;
    private final AuditLogger auditLogger;

    public KnowledgeIngestionService(KnowledgeDocumentRepository documentRepository,
                                      DocumentChunkRepository chunkRepository,
                                      EmbeddingGateway embeddingGateway,
                                    PersonaRepository personaRepository, 
                                    AuditLogger auditLogger) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingGateway = embeddingGateway;
        this.personaRepository = personaRepository;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public UUID ingest(UUID scenarioId, UUID personaId, KnowledgeCollection collection,
                        String title, String sourceText) {
        // calls to create document
        UUID documentId = ingestDocument(scenarioId, personaId, collection, title, sourceText);
        auditLogger.recordAdmin(AuditAction.ADMIN_SCENARIO_DOCUMENT_ADDED, "KNOWLEDGE_DOCUMENT", documentId.toString(), "scenario " + scenarioId + ", title: " + title);
        return documentId;
    }

    // separated from ingest() to prevent multiple audit events from copying documents into the new scenario revision
    @Transactional
    private UUID ingestDocument(UUID scenarioId, UUID personaId, KnowledgeCollection collection,
                        String title, String sourceText) {
                            
        requirePersonaInScenario(personaId, scenarioId);
        KnowledgeDocument document = KnowledgeDocument.create(scenarioId, personaId, collection, title, sourceText);
        documentRepository.save(document);

        List<String> chunks = chunk(sourceText);
        List<DocumentChunk> entities = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            float[] embedding = embeddingGateway.embed(chunks.get(i));
            entities.add(DocumentChunk.create(document.getId(), scenarioId, personaId, collection, i,
                    chunks.get(i), embedding));
        }
        chunkRepository.saveAll(entities);
        return document.getId();
    }

    /** Copies authored source documents into a scenario revision and reindexes them under its new scope. */
    @Transactional
    public void copyScenarioDocuments(UUID sourceScenarioId, UUID targetScenarioId, Map<UUID, UUID> personaIdMap) {
        for (KnowledgeDocument document : documentRepository.findByScenarioId(sourceScenarioId)) {
            UUID targetPersonaId = document.getPersonaId() == null ? null : personaIdMap.get(document.getPersonaId());
            ingestDocument(targetScenarioId, targetPersonaId, document.getCollection(), document.getTitle(), document.getSourceText());
        }
    }

    private List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        for (String paragraph : PARAGRAPH_BREAK.split(text)) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() <= CHUNK_TARGET_CHARS) {
                chunks.add(trimmed);
            } else {
                for (int start = 0; start < trimmed.length(); start += CHUNK_TARGET_CHARS) {
                    chunks.add(trimmed.substring(start, Math.min(trimmed.length(), start + CHUNK_TARGET_CHARS)));
                }
            }
        }
        return chunks.isEmpty() ? List.of(text.trim()) : chunks;
    }

    /** Lists all knowledge documents ingested for a scenario, across all collections/personas. */
    @Transactional(readOnly = true)
    public List<KnowledgeDocument> list(UUID scenarioId) {
        return documentRepository.findByScenarioId(scenarioId);
    }

    /** Removes a knowledge document and all chunks derived from it. */
    @Transactional
    public void delete(UUID scenarioId, UUID documentId) {
        KnowledgeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("KnowledgeDocument", documentId));

        requireDocumentInScenario(document, scenarioId);

        chunkRepository.deleteByDocumentId(documentId);
        documentRepository.deleteById(documentId);

        auditLogger.recordAdmin(AuditAction.ADMIN_SCENARIO_DOCUMENT_DELETED, "KNOWLEDGE_DOCUMENT", documentId.toString(), "scenario " + scenarioId + ", title: " + document.getTitle());
    }

    @Transactional
    public void update(UUID scenarioId, UUID documentId, UUID newPersonaId, KnowledgeCollection newCollection,
                        String newTitle, String newSourceText) {
        KnowledgeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new com.ibm.consulting.sim.shared.domain.NotFoundException("KnowledgeDocument", documentId));

        requireDocumentInScenario(document, scenarioId);
        requirePersonaInScenario(newPersonaId, scenarioId);
        document.updateContent(newPersonaId, newCollection, newTitle, newSourceText);
        documentRepository.save(document);

        chunkRepository.deleteByDocumentId(documentId); // old chunks carry the OLD persona/collection scope too
        reindex(documentId, document.getScenarioId(), newPersonaId, newCollection, newSourceText);

        auditLogger.recordAdmin(AuditAction.ADMIN_SCENARIO_DOCUMENT_UPDATED, "KNOWLEDGE_DOCUMENT", documentId.toString(), "scenario " + scenarioId + ", title: " + newTitle);
    }

    private void reindex(UUID documentId, UUID scenarioId, UUID personaId, KnowledgeCollection collection, String sourceText) {
        List<String> chunks = chunk(sourceText);
        List<DocumentChunk> entities = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            float[] embedding = embeddingGateway.embed(chunks.get(i));
            entities.add(DocumentChunk.create(documentId, scenarioId, personaId, collection, i, chunks.get(i), embedding));
        }
        chunkRepository.saveAll(entities);
    }
    private void requireDocumentInScenario(KnowledgeDocument document, UUID scenarioId) {
        if (!document.getScenarioId().equals(scenarioId)) {
            throw new NotFoundException("KnowledgeDocument", document.getId());
        }
    }

    
    private void requirePersonaInScenario(UUID personaId, UUID scenarioId) {
        if (personaId == null) return;
        Persona persona = personaRepository.findById(personaId)
                .orElseThrow(() -> new NotFoundException("Persona", personaId));
        if (!persona.getScenario().getId().equals(scenarioId)) {
            throw new IllegalArgumentException(
                    "Persona " + personaId + " does not belong to scenario " + scenarioId);
        }
    }
}
