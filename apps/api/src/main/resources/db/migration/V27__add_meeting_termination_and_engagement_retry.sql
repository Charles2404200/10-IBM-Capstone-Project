-- Additive migration: existing meeting and engagement records remain valid.
ALTER TABLE meetings ADD COLUMN IF NOT EXISTS termination_reason VARCHAR(50);
ALTER TABLE meetings ADD COLUMN IF NOT EXISTS termination_message TEXT;

ALTER TABLE engagements ADD COLUMN IF NOT EXISTS retry_of_engagement_id UUID;
CREATE INDEX IF NOT EXISTS idx_engagements_retry_of ON engagements(retry_of_engagement_id);
