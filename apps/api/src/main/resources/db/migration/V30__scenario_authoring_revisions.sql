-- Scenario authoring remains additive: existing scenario IDs continue to back
-- existing engagements, while a published revision receives its own row/ID.
ALTER TABLE scenarios ADD COLUMN IF NOT EXISTS scenario_lineage_id UUID;
ALTER TABLE scenarios ADD COLUMN IF NOT EXISTS authoring_config TEXT;

UPDATE scenarios
SET scenario_lineage_id = id
WHERE scenario_lineage_id IS NULL;

ALTER TABLE scenarios ALTER COLUMN scenario_lineage_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_scenarios_lineage_status
    ON scenarios (scenario_lineage_id, status);
