-- Additive audit trail for Simulation Director decisions. Existing meetings keep an empty ledger.
CREATE TABLE meeting_behaviour_ledger (
    meeting_id           UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    ledger_order         INT NOT NULL,
    learner_sequence     INT NOT NULL,
    quality              VARCHAR(64) NOT NULL,
    trust_delta          INT NOT NULL,
    interest_delta       INT NOT NULL,
    patience_delta       INT NOT NULL,
    verified_behaviours  TEXT,
    explanation          TEXT NOT NULL,
    next_best_action     TEXT NOT NULL,
    PRIMARY KEY (meeting_id, ledger_order)
);

CREATE INDEX idx_meeting_behaviour_ledger_meeting
    ON meeting_behaviour_ledger (meeting_id, learner_sequence);
