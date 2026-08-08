CREATE TABLE outreach_attempts (
    id                      UUID PRIMARY KEY,
    engagement_id           UUID NOT NULL REFERENCES engagements(id),
    attempt_number          INT NOT NULL,
    subject                 VARCHAR(200) NOT NULL,
    body                    TEXT NOT NULL,
    client_reply            TEXT,
    outcome                 VARCHAR(30) NOT NULL,
    score_personalisation   INT,
    score_relevance         INT,
    score_clarity           INT,
    score_call_to_action    INT,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_outreach_engagement ON outreach_attempts (engagement_id);
