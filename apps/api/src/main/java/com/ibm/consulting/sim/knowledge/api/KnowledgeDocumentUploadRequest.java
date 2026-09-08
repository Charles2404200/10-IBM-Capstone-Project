package com.ibm.consulting.sim.knowledge.api;

import com.ibm.consulting.sim.knowledge.domain.KnowledgeCollection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record KnowledgeDocumentUploadRequest(
        UUID personaId,
        @NotNull KnowledgeCollection collection,
        @NotBlank @Size(max = KnowledgeDocumentLimits.MAX_TITLE_LENGTH) String title,
        @NotBlank @Size(max = KnowledgeDocumentLimits.MAX_CONTENT_LENGTH) String content) {
}
