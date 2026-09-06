package com.ibm.consulting.sim.knowledge.infrastructure;

import com.ibm.consulting.sim.knowledge.domain.DocumentChunk;
import com.ibm.consulting.sim.knowledge.domain.DocumentChunkRepository;
import com.ibm.consulting.sim.knowledge.domain.KnowledgeCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
interface SpringDataDocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    @Query("""
            SELECT c FROM DocumentChunk c
            WHERE c.collection = :collection
              AND (c.scenarioId IS NULL OR c.scenarioId = :scenarioId)
              AND (c.personaId IS NULL OR c.personaId = :personaId)
            """)
    List<DocumentChunk> findInScope(@Param("collection") KnowledgeCollection collection,
                                     @Param("scenarioId") UUID scenarioId,
                                     @Param("personaId") UUID personaId);
    void deleteByDocumentId(UUID documentId);
}

@Repository
class JpaDocumentChunkRepository implements DocumentChunkRepository {

    private final SpringDataDocumentChunkRepository repo;

    JpaDocumentChunkRepository(SpringDataDocumentChunkRepository repo) {
        this.repo = repo;
    }

    @Override public DocumentChunk save(DocumentChunk chunk) { return repo.save(chunk); }
    @Override public List<DocumentChunk> saveAll(List<DocumentChunk> chunks) { return repo.saveAll(chunks); }

    @Override
    public List<DocumentChunk> findByCollectionAndScope(KnowledgeCollection collection, UUID scenarioId, UUID personaId) {
        return repo.findInScope(collection, scenarioId, personaId);
    }
    
    @Override public void deleteByDocumentId(UUID documentId) { 
        repo.deleteByDocumentId(documentId); 
    }
}
