-- Existing assessments already contain their historical coaching, so they are
-- considered ready. New assessments are first persisted as PENDING and then
-- enriched asynchronously after commit.
ALTER TABLE assessments
    ADD COLUMN IF NOT EXISTS feedback_status VARCHAR(16) NOT NULL DEFAULT 'READY';
