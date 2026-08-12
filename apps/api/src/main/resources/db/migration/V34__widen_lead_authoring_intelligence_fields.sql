-- Authoring accepts natural, client-specific intelligence. Earlier revisions
-- used short VARCHAR limits which turn valid consulting context into a 500
-- database truncation error. Widening is backward compatible: no values are
-- transformed or removed, and existing engagements retain their snapshots.
ALTER TABLE leads
    ALTER COLUMN potential_value_range TYPE TEXT,
    ALTER COLUMN decision_maker TYPE TEXT,
    ALTER COLUMN technology_stack TYPE TEXT,
    ALTER COLUMN budget_signal TYPE TEXT,
    ALTER COLUMN pain_severity TYPE TEXT;

ALTER TABLE lead_signals
    ALTER COLUMN label TYPE TEXT,
    ALTER COLUMN category TYPE TEXT;
