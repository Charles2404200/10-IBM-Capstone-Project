CREATE TABLE meeting_preparations (
    id             UUID PRIMARY KEY,
    engagement_id  UUID NOT NULL UNIQUE REFERENCES engagements(id),
    objective      TEXT,
    readiness_score INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE meeting_preparation_agenda (
    preparation_id UUID NOT NULL REFERENCES meeting_preparations(id) ON DELETE CASCADE,
    position       INT NOT NULL,
    item           TEXT
);

CREATE TABLE meeting_preparation_questions (
    preparation_id UUID NOT NULL REFERENCES meeting_preparations(id) ON DELETE CASCADE,
    position       INT NOT NULL,
    item           TEXT
);

CREATE TABLE meetings (
    id            UUID PRIMARY KEY,
    engagement_id UUID NOT NULL REFERENCES engagements(id),
    persona_id    UUID NOT NULL REFERENCES personas(id),
    status        VARCHAR(20) NOT NULL,
    completed_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE conversation_turns (
    id          UUID PRIMARY KEY,
    meeting_id  UUID NOT NULL REFERENCES meetings(id),
    actor       VARCHAR(20) NOT NULL,
    content     TEXT NOT NULL,
    sequence    INT NOT NULL,
    signals     TEXT,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE persona_states (
    id            UUID PRIMARY KEY,
    engagement_id UUID NOT NULL UNIQUE REFERENCES engagements(id),
    trust         INT NOT NULL,
    interest      INT NOT NULL,
    patience      INT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE persona_state_disclosed_facts (
    persona_state_id UUID NOT NULL REFERENCES persona_states(id) ON DELETE CASCADE,
    fact_id          VARCHAR(200) NOT NULL
);

CREATE INDEX idx_meetings_engagement ON meetings (engagement_id);
CREATE INDEX idx_conversation_turns_meeting ON conversation_turns (meeting_id);
