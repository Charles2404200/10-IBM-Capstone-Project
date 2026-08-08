package com.ibm.consulting.sim.knowledge.api;

import com.ibm.consulting.sim.knowledge.domain.KnowledgeCollection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record KnowledgeDocumentUploadRequest(
        UUID personaId,
        @NotNull KnowledgeCollection collection,
        @NotBlank String title,
        @NotBlank String content) {
}
