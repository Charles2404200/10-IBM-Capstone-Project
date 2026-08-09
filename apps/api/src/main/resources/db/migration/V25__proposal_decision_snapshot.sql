-- Additive decision snapshot. Existing submitted proposals remain readable through
-- response fallbacks until they are viewed in the upgraded outcome experience.
ALTER TABLE proposals ADD COLUMN IF NOT EXISTS client_decision_outcome VARCHAR(40);
ALTER TABLE proposals ADD COLUMN IF NOT EXISTS decision_confidence INT;
ALTER TABLE proposals ADD COLUMN IF NOT EXISTS learner_performance_score INT;

CREATE TABLE proposal_decision_dimensions (
    proposal_id UUID NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    position INT NOT NULL,
    dimension VARCHAR(80),
    score INT NOT NULL,
    interpretation TEXT,
    PRIMARY KEY (proposal_id, position)
);

CREATE TABLE proposal_decision_insights (
    proposal_id UUID NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    position INT NOT NULL,
    category VARCHAR(30),
    detail TEXT,
    PRIMARY KEY (proposal_id, position)
);

CREATE TABLE proposal_evidence_impacts (
    proposal_id UUID NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    position INT NOT NULL,
    claim TEXT,
    support_level VARCHAR(30),
    explanation TEXT,
    PRIMARY KEY (proposal_id, position)
);
