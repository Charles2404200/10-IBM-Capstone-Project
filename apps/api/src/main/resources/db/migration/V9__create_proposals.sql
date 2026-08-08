CREATE TABLE proposals (
    id                 UUID PRIMARY KEY,
    engagement_id      UUID NOT NULL UNIQUE REFERENCES engagements(id),
    problem_statement  TEXT NOT NULL,
    budget             NUMERIC(14,2) NOT NULL,
    timeline_weeks     INT NOT NULL,
    alignment_score    INT NOT NULL DEFAULT 0,
    decision           VARCHAR(20) NOT NULL,
    decision_rationale TEXT,
    submitted_at       TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE proposal_components (
    proposal_id UUID NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    position    INT NOT NULL,
    item        TEXT
);
