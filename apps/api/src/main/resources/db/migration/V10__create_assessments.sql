CREATE TABLE assessments (
    id              UUID PRIMARY KEY,
    engagement_id   UUID NOT NULL UNIQUE REFERENCES engagements(id),
    overall_score   INT NOT NULL,
    outcome         VARCHAR(30) NOT NULL,
    feedback_summary TEXT,
    generated_at    TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE assessment_competency_scores (
    assessment_id   UUID NOT NULL REFERENCES assessments(id) ON DELETE CASCADE,
    competency_name VARCHAR(100) NOT NULL,
    score           INT NOT NULL,
    evidence_note   TEXT
);

CREATE TABLE assessment_strengths (
    assessment_id UUID NOT NULL REFERENCES assessments(id) ON DELETE CASCADE,
    item          TEXT
);

CREATE TABLE assessment_improvement_areas (
    assessment_id UUID NOT NULL REFERENCES assessments(id) ON DELETE CASCADE,
    item          TEXT
);
