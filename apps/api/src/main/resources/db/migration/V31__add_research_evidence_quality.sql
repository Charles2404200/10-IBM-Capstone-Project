-- Additive quality metadata. Existing learner evidence remains valid and is
-- assigned a neutral relevance score until it is corroborated by later work.
ALTER TABLE research_evidence
    ADD COLUMN IF NOT EXISTS relevance_score INTEGER NOT NULL DEFAULT 60;

ALTER TABLE research_evidence
    DROP CONSTRAINT IF EXISTS research_evidence_relevance_score_check;

ALTER TABLE research_evidence
    ADD CONSTRAINT research_evidence_relevance_score_check
        CHECK (relevance_score BETWEEN 0 AND 100);
