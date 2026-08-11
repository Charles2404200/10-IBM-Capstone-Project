package com.ibm.consulting.sim.knowledge.infrastructure;

import com.ibm.consulting.sim.knowledge.domain.KnowledgeDocument;
import com.ibm.consulting.sim.knowledge.domain.KnowledgeDocumentRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
interface SpringDataKnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {
    java.util.List<KnowledgeDocument> findByScenarioId(UUID scenarioId);
}

@Repository
class JpaKnowledgeDocumentRepository implements KnowledgeDocumentRepository {

    private final SpringDataKnowledgeDocumentRepository repo;

    JpaKnowledgeDocumentRepository(SpringDataKnowledgeDocumentRepository repo) {
        this.repo = repo;
    }

    @Override public KnowledgeDocument save(KnowledgeDocument document) { return repo.save(document); }
    @Override public java.util.List<KnowledgeDocument> findByScenarioId(UUID scenarioId) { return repo.findByScenarioId(scenarioId); }
}
