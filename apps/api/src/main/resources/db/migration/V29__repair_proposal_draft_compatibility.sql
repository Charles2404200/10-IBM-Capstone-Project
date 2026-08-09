-- Defensive, additive repair for installations that received the original proposal
-- schema before drafts were introduced. A draft is intentionally not submitted.
ALTER TABLE proposals ALTER COLUMN submitted_at DROP NOT NULL;

ALTER TABLE proposals ADD COLUMN IF NOT EXISTS solution_strategy TEXT;
ALTER TABLE proposals ADD COLUMN IF NOT EXISTS budget_confidence VARCHAR(20);
ALTER TABLE proposals ADD COLUMN IF NOT EXISTS budget_source TEXT;
ALTER TABLE proposals ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED';

CREATE TABLE IF NOT EXISTS proposal_business_outcomes (
    proposal_id UUID NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    position INT NOT NULL,
    outcome TEXT,
    metric TEXT,
    target TEXT,
    PRIMARY KEY (proposal_id, position)
);

CREATE TABLE IF NOT EXISTS proposal_milestones (
    proposal_id UUID NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    position INT NOT NULL,
    phase TEXT,
    duration TEXT,
    PRIMARY KEY (proposal_id, position)
);

CREATE TABLE IF NOT EXISTS proposal_risks (
    proposal_id UUID NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    position INT NOT NULL,
    risk TEXT,
    severity VARCHAR(20),
    mitigation TEXT,
    PRIMARY KEY (proposal_id, position)
);

CREATE TABLE IF NOT EXISTS proposal_assumptions (
    proposal_id UUID NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    position INT NOT NULL,
    item TEXT,
    PRIMARY KEY (proposal_id, position)
);

CREATE TABLE IF NOT EXISTS proposal_evidence_links (
    proposal_id UUID NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    position INT NOT NULL,
    section VARCHAR(60),
    source_id VARCHAR(100),
    PRIMARY KEY (proposal_id, position)
);
