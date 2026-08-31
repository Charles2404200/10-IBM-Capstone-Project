package com.ibm.consulting.sim.knowledge.api;

import com.ibm.consulting.sim.knowledge.application.KnowledgeIngestionService;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.scenario.domain.ScenarioStatus;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import com.ibm.consulting.sim.shared.domain.DomainException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.List;

/**
 * Admin-only endpoint for ingesting scenario knowledge (truth documents,
 * consulting practice notes, assessment rubrics) into the RAG pipeline (§8 Phase 3).
 * Restricted to authors/administrators since ingested content directly grounds
 * persona behaviour and assessment feedback.
 */
@RestController
@RequestMapping("/api/v1/admin/scenarios/{scenarioId}/documents")
@PreAuthorize("hasAnyRole('SCENARIO_AUTHOR', 'ADMINISTRATOR')")
public class KnowledgeController {

    private final KnowledgeIngestionService ingestionService;
    private final ScenarioRepository scenarioRepository;

    public KnowledgeController(KnowledgeIngestionService ingestionService, ScenarioRepository scenarioRepository) {
        this.ingestionService = ingestionService;
        this.scenarioRepository = scenarioRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> upload(@PathVariable UUID scenarioId,
                             @Valid @RequestBody KnowledgeDocumentUploadRequest request) {
        requireDraft(scenarioId);
        UUID documentId = ingestionService.ingest(scenarioId, request.personaId(), request.collection(),
                request.title(), request.content());
        return Map.of("documentId", documentId);
    }



    // add this method inside the class, after upload()
    @GetMapping
    List<KnowledgeDocumentSummary> list(@PathVariable UUID scenarioId) {
        return ingestionService.list(scenarioId).stream()
                .map(KnowledgeDocumentSummary::from)
                .toList();
    }

    static class ScenarioContentLockedException extends DomainException {
        ScenarioContentLockedException(UUID scenarioId) {
            super("Scenario " + scenarioId + " is published. Create a draft revision before changing knowledge.");
        }
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID scenarioId, @PathVariable UUID documentId) {
        requireDraft(scenarioId);
        ingestionService.delete(documentId);
    }

    // Get this code from upload() and make it a shared method.
    private void requireDraft(UUID scenarioId) {
        Scenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new NotFoundException("Scenario", scenarioId));
        if (scenario.getStatus() != ScenarioStatus.DRAFT) {
            throw new ScenarioContentLockedException(scenarioId);
        }
    }
}
