package com.ibm.consulting.sim.knowledge.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ibm.consulting.sim.ai.domain.EmbeddingGateway;
import com.ibm.consulting.sim.knowledge.domain.DocumentChunkRepository;
import com.ibm.consulting.sim.knowledge.domain.KnowledgeCollection;
import com.ibm.consulting.sim.knowledge.domain.KnowledgeDocument;
import com.ibm.consulting.sim.knowledge.domain.KnowledgeDocumentRepository;
import com.ibm.consulting.sim.scenario.domain.Persona;
import com.ibm.consulting.sim.scenario.domain.PersonaRepository;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditAction;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditLogger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestionServiceTest {

    @Mock KnowledgeDocumentRepository documentRepository;
    @Mock DocumentChunkRepository chunkRepository;
    @Mock EmbeddingGateway embeddingGateway;
    @Mock PersonaRepository personaRepository;
    @Mock AuditLogger auditLogger;
    @InjectMocks KnowledgeIngestionService service;

    @Test
    void update_rejectsDocumentFromAnotherScenario() {
        UUID actualScenarioId = UUID.randomUUID();
        UUID attackerSuppliedScenarioId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        KnowledgeDocument document = KnowledgeDocument.create(
                actualScenarioId, null, KnowledgeCollection.SCENARIO_TRUTH, "Real doc", "Real content");
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.update(
                attackerSuppliedScenarioId, documentId, null, KnowledgeCollection.SCENARIO_TRUTH,
                "Tampered title", "Tampered content"))
            .isInstanceOf(NotFoundException.class);

        verify(documentRepository, never()).save(any());
        verify(chunkRepository, never()).deleteByDocumentId(any());
    }

    @Test
    void update_rejectsPersonaFromAnotherScenario() {
        UUID scenarioId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID foreignPersonaId = UUID.randomUUID();

        KnowledgeDocument document = KnowledgeDocument.create(
                scenarioId, null, KnowledgeCollection.SCENARIO_TRUTH, "Doc", "Content");
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        Scenario otherScenario = Scenario.create("Other Scenario", "Retail", "Unrelated scenario", 3);
        Persona foreignPersona = Persona.create(otherScenario, "Name", "Title", "Org", null, null, null, null);
        when(personaRepository.findById(foreignPersonaId)).thenReturn(Optional.of(foreignPersona));

        assertThatThrownBy(() -> service.update(
                scenarioId, documentId, foreignPersonaId, KnowledgeCollection.SCENARIO_TRUTH, "Title", "Content"))
            .isInstanceOf(IllegalArgumentException.class);

        verify(documentRepository, never()).save(any());
    }

    @Test
    void delete_rejectsDocumentFromAnotherScenario() {
        UUID actualScenarioId = UUID.randomUUID();
        UUID attackerSuppliedScenarioId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        KnowledgeDocument document = KnowledgeDocument.create(
                actualScenarioId, null, KnowledgeCollection.SCENARIO_TRUTH, "Doc", "Content");
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.delete(attackerSuppliedScenarioId, documentId))
            .isInstanceOf(NotFoundException.class);

        verify(documentRepository, never()).deleteById(any());
        verify(chunkRepository, never()).deleteByDocumentId(any());
    }

    // audit logging - add document
    @Test
    void logsDocumentAdded() {
        UUID scenarioId = UUID.randomUUID();

        // mock document save and embedding
        when(documentRepository.save(any(KnowledgeDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(embeddingGateway.embed(any())).thenReturn(new float[] {1.0f, 2.0f});

        // create document
        UUID documentId = service.ingest(scenarioId, null, KnowledgeCollection.SCENARIO_TRUTH, "Test document", "Test content");
        verify(auditLogger).recordAdmin(
            eq(AuditAction.ADMIN_SCENARIO_DOCUMENT_ADDED),
            eq("KNOWLEDGE_DOCUMENT"),
            eq(documentId.toString()),
            contains("scenario " + scenarioId));
    }

    // audit logging - update document
    @Test
    void logsDocumentUpdated() {
        UUID scenarioId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        // mock existing document
        KnowledgeDocument document = org.mockito.Mockito.mock(KnowledgeDocument.class);
        when(document.getScenarioId()).thenReturn(scenarioId);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(embeddingGateway.embed(any())).thenReturn(new float[] {1.0f, 2.0f});

        // update document
        service.update(scenarioId, documentId, null, KnowledgeCollection.SCENARIO_TRUTH, "Updated title", "Updated content");

        verify(auditLogger).recordAdmin(
            eq(AuditAction.ADMIN_SCENARIO_DOCUMENT_UPDATED),
            eq("KNOWLEDGE_DOCUMENT"),
            eq(documentId.toString()),
            contains("Updated title"));
    }

    // audit logging - delete document
    @Test
    void logsDocumentDeleted() {
        UUID scenarioId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        // mock existing document
        KnowledgeDocument document = org.mockito.Mockito.mock(KnowledgeDocument.class);
        when(document.getScenarioId()).thenReturn(scenarioId);
        when(document.getTitle()).thenReturn("Test document");
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        // deletes document
        service.delete(scenarioId, documentId);

        verify(auditLogger).recordAdmin(
            eq(AuditAction.ADMIN_SCENARIO_DOCUMENT_DELETED),
            eq("KNOWLEDGE_DOCUMENT"),
            eq(documentId.toString()),
            contains("Test document"));
    }

    // ensures copying documents doesn't repeat audit
    @Test
    void skipsAuditWhenCopying() {
        UUID sourceScenarioId = UUID.randomUUID();
        UUID targetScenarioId = UUID.randomUUID();

        // mock existing document
        KnowledgeDocument document = org.mockito.Mockito.mock(KnowledgeDocument.class);
        when(document.getPersonaId()).thenReturn(null);
        when(document.getCollection()).thenReturn(KnowledgeCollection.SCENARIO_TRUTH);
        when(document.getTitle()).thenReturn("Copied document");
        when(document.getSourceText()).thenReturn("Copied content");
        when(documentRepository.findByScenarioId(sourceScenarioId)).thenReturn(List.of(document));
        when(embeddingGateway.embed(any())).thenReturn(new float[] {1.0f, 2.0f});

        // calls to copy scenarios
        service.copyScenarioDocuments(sourceScenarioId, targetScenarioId, Map.of());
        
        // should return no logs
        verify(auditLogger, never()).recordAdmin(
            eq(AuditAction.ADMIN_SCENARIO_DOCUMENT_ADDED),
            any(), any(), any());
    }
}