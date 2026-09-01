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

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestionServiceTest {

    @Mock KnowledgeDocumentRepository documentRepository;
    @Mock DocumentChunkRepository chunkRepository;
    @Mock EmbeddingGateway embeddingGateway;
    @Mock PersonaRepository personaRepository;
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
}
