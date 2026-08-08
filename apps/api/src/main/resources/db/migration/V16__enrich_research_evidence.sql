-- Phase 3 follow-up: enterprise-grade evidence traceability.
-- Adds source metadata (traceability), a stable per-engagement display
-- sequence ("E-01", "E-02", ...), a confidence rating, and a self-referencing
-- link table so a HYPOTHESIS-type evidence row can cite the evidence rows
-- that support it (AI assessment can later evaluate hypothesis quality
-- against its cited evidence).

ALTER TABLE research_evidence
    ADD COLUMN source_url     VARCHAR(500),
    ADD COLUMN source_title   VARCHAR(300),
    ADD COLUMN occurred_on    DATE,
    ADD COLUMN confidence     VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN sequence_no    INTEGER;

-- Backfill sequence numbers for any pre-existing rows, ordered by creation.
WITH ordered AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY engagement_id ORDER BY created_at) AS rn
    FROM research_evidence
)
UPDATE research_evidence r
SET sequence_no = ordered.rn
FROM ordered
WHERE r.id = ordered.id;

ALTER TABLE research_evidence
    ALTER COLUMN sequence_no SET NOT NULL;

CREATE TABLE research_evidence_links (
    evidence_id         UUID NOT NULL REFERENCES research_evidence(id) ON DELETE CASCADE,
    linked_evidence_id  UUID NOT NULL REFERENCES research_evidence(id) ON DELETE CASCADE,
    PRIMARY KEY (evidence_id, linked_evidence_id)
);

CREATE INDEX idx_research_evidence_links_evidence ON research_evidence_links (evidence_id);
