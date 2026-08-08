package com.ibm.consulting.sim.knowledge.api;

import com.ibm.consulting.sim.knowledge.application.KnowledgeIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

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

    public KnowledgeController(KnowledgeIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> upload(@PathVariable UUID scenarioId,
                             @Valid @RequestBody KnowledgeDocumentUploadRequest request) {
        UUID documentId = ingestionService.ingest(scenarioId, request.personaId(), request.collection(),
                request.title(), request.content());
        return Map.of("documentId", documentId);
    }
}
