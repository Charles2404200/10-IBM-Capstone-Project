package com.ibm.consulting.sim.knowledge.domain;

import java.util.List;
import java.util.UUID;

public interface KnowledgeDocumentRepository {
    KnowledgeDocument save(KnowledgeDocument document);
    List<KnowledgeDocument> findByScenarioId(UUID scenarioId);
}
