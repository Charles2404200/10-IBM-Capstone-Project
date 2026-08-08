CREATE TABLE knowledge_documents (
    id           UUID PRIMARY KEY,
    scenario_id  UUID,
    persona_id   UUID,
    collection   VARCHAR(40) NOT NULL,
    title        VARCHAR(255) NOT NULL,
    source_text  TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_knowledge_documents_scope ON knowledge_documents(scenario_id, persona_id, collection);

-- Embeddings are stored as a comma-separated float string rather than a pgvector
-- column: this is a deliberate MVP portability trade-off (see PHASE_3_DESIGN_PATTERNS.md)
-- that avoids a hard dependency on the pgvector extension on the hosted Postgres
-- instance. Cosine similarity is computed in the application layer instead.
CREATE TABLE document_chunks (
    id           UUID PRIMARY KEY,
    document_id  UUID NOT NULL REFERENCES knowledge_documents(id) ON DELETE CASCADE,
    scenario_id  UUID,
    persona_id   UUID,
    collection   VARCHAR(40) NOT NULL,
    chunk_index  INT NOT NULL,
    content      TEXT NOT NULL,
    embedding    TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_document_chunks_scope ON document_chunks(collection, scenario_id, persona_id);
