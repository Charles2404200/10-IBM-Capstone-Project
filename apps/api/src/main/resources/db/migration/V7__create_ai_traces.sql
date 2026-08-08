CREATE TABLE ai_traces (
    id             UUID PRIMARY KEY,
    use_case       VARCHAR(60) NOT NULL,
    engagement_id  UUID,
    model          VARCHAR(120) NOT NULL,
    prompt_version INT NOT NULL,
    latency_ms     BIGINT NOT NULL,
    status         VARCHAR(30) NOT NULL,
    error_message  TEXT,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_ai_traces_engagement ON ai_traces (engagement_id);
CREATE INDEX idx_ai_traces_use_case ON ai_traces (use_case);
