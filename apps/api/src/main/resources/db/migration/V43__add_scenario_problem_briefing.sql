ALTER TABLE scenarios
    ADD COLUMN business_situation TEXT NOT NULL DEFAULT '',
    ADD COLUMN observable_symptom TEXT NOT NULL DEFAULT '',
    ADD COLUMN consulting_mandate TEXT NOT NULL DEFAULT '',
    ADD COLUMN unknowns_to_validate TEXT NOT NULL DEFAULT '';

-- Preserve every existing scenario without inventing scenario truth. Authors can
-- refine these inherited values into a richer problem frame in the admin workspace.
UPDATE scenarios
SET business_situation = COALESCE(NULLIF(TRIM(description), ''), ''),
    observable_symptom = COALESCE(NULLIF(TRIM(description), ''), ''),
    consulting_mandate = COALESCE(NULLIF(TRIM(objective), ''), ''),
    unknowns_to_validate = COALESCE(NULLIF(TRIM(success_criteria), ''), '');
