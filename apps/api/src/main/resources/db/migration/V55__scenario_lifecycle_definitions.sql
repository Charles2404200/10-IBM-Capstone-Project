-- Null definitions preserve seeded scenarios and runs from earlier releases.
-- Resolved definitions are snapshotted by the application when a run starts.
ALTER TABLE scenarios ADD COLUMN lifecycle_definition text;
ALTER TABLE engagements ADD COLUMN lifecycle_definition_snapshot text;
