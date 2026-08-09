ALTER TABLE outreach_attempts ADD COLUMN next_action VARCHAR(40);

CREATE TABLE capability_briefs (
    id                        UUID PRIMARY KEY,
    engagement_id             UUID NOT NULL UNIQUE REFERENCES engagements(id) ON DELETE CASCADE,
    relevant_experience       TEXT NOT NULL,
    approach                  TEXT NOT NULL,
    case_example              TEXT NOT NULL,
    client_fit                TEXT NOT NULL,
    client_reply              TEXT,
    outcome                   VARCHAR(30) NOT NULL,
    score_client_fit          INTEGER,
    score_industry_relevance  INTEGER,
    score_evidence_quality    INTEGER,
    score_clarity             INTEGER,
    score_credibility         INTEGER,
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    version                   BIGINT NOT NULL DEFAULT 0
);
