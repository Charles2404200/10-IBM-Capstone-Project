CREATE TABLE engagements (
    id               UUID PRIMARY KEY,
    user_id          UUID NOT NULL REFERENCES users(id),
    scenario_id      UUID NOT NULL REFERENCES scenarios(id),
    persona_id       UUID NOT NULL REFERENCES personas(id),
    state            VARCHAR(30) NOT NULL,
    selected_lead_id UUID REFERENCES leads(id),
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE engagement_events (
    id            UUID PRIMARY KEY,
    engagement_id UUID NOT NULL REFERENCES engagements(id),
    state         VARCHAR(30) NOT NULL,
    description   TEXT NOT NULL,
    occurred_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_engagements_user ON engagements (user_id);
CREATE INDEX idx_engagement_events_engagement ON engagement_events (engagement_id);
