CREATE TABLE scenarios (
    id          UUID PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    industry    VARCHAR(100) NOT NULL,
    description TEXT,
    status      VARCHAR(20) NOT NULL,
    difficulty      INT NOT NULL CHECK (difficulty BETWEEN 1 AND 5),
    content_version INT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE personas (
    id                  UUID PRIMARY KEY,
    scenario_id         UUID NOT NULL REFERENCES scenarios(id),
    name                VARCHAR(100) NOT NULL,
    job_title           VARCHAR(100) NOT NULL,
    organisation        VARCHAR(200) NOT NULL,
    communication_style TEXT,
    visible_concerns    TEXT,
    hidden_concerns     TEXT,
    business_goals      TEXT,
    prompt_version      INT NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_scenarios_status ON scenarios (status);
CREATE INDEX idx_personas_scenario ON personas (scenario_id);
