package com.ibm.consulting.sim.knowledge.application;

import com.ibm.consulting.sim.ai.domain.EmbeddingGateway;
import com.ibm.consulting.sim.knowledge.domain.DocumentChunk;
import com.ibm.consulting.sim.knowledge.domain.DocumentChunkRepository;
import com.ibm.consulting.sim.knowledge.domain.KnowledgeCollection;
import com.ibm.consulting.sim.knowledge.domain.KnowledgeDocument;
import com.ibm.consulting.sim.knowledge.domain.KnowledgeDocumentRepository;
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

    public KnowledgeIngestionService(KnowledgeDocumentRepository documentRepository,
                                      DocumentChunkRepository chunkRepository,
                                      EmbeddingGateway embeddingGateway) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingGateway = embeddingGateway;
    }

    @Transactional
    public UUID ingest(UUID scenarioId, UUID personaId, KnowledgeCollection collection,
                        String title, String sourceText) {
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
            ingest(targetScenarioId, targetPersonaId, document.getCollection(), document.getTitle(), document.getSourceText());
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
    public void delete(UUID documentId) {
        if (!documentRepository.findById(documentId).isPresent()) {
            throw new com.ibm.consulting.sim.shared.domain.NotFoundException("KnowledgeDocument", documentId);
        }
        chunkRepository.deleteByDocumentId(documentId);
        documentRepository.deleteById(documentId);
    }

    @Transactional
    public void update(UUID documentId, UUID newPersonaId, KnowledgeCollection newCollection,
                        String newTitle, String newSourceText) {
        KnowledgeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new com.ibm.consulting.sim.shared.domain.NotFoundException("KnowledgeDocument", documentId));

        document.updateContent(newPersonaId, newCollection, newTitle, newSourceText);
        documentRepository.save(document);

        chunkRepository.deleteByDocumentId(documentId); // old chunks carry the OLD persona/collection scope too
        reindex(documentId, document.getScenarioId(), newPersonaId, newCollection, newSourceText);
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
}
