CREATE TABLE leads (
    id                  UUID PRIMARY KEY,
    scenario_id         UUID NOT NULL REFERENCES scenarios(id),
    company_name        VARCHAR(200) NOT NULL,
    industry            VARCHAR(100) NOT NULL,
    public_description  TEXT,
    difficulty          VARCHAR(10) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE lead_signals (
    id          UUID PRIMARY KEY,
    lead_id     UUID NOT NULL REFERENCES leads(id),
    label       VARCHAR(200) NOT NULL,
    category    VARCHAR(80) NOT NULL
);

CREATE TABLE research_evidence (
    id              UUID PRIMARY KEY,
    engagement_id   UUID NOT NULL,
    lead_id         UUID NOT NULL REFERENCES leads(id),
    note            TEXT NOT NULL,
    hypothesis      TEXT,
    evidence_type   VARCHAR(40) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_leads_scenario ON leads (scenario_id);
CREATE INDEX idx_research_engagement ON research_evidence (engagement_id);
