-- Adds per-scenario rubric weight customisation so scenario authors can tune how
-- much each competency contributes to the overall assessment score.
ALTER TABLE scenarios ADD COLUMN rubric_weights TEXT;
