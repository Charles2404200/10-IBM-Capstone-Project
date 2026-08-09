-- Additive JSON snapshots: existing scenarios/engagements transparently fall back
-- to the deterministic profile derived from their current difficulty fields.
ALTER TABLE scenarios ADD COLUMN IF NOT EXISTS difficulty_profile_config TEXT;
ALTER TABLE engagements ADD COLUMN IF NOT EXISTS difficulty_profile_snapshot TEXT;
