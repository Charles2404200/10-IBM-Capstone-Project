-- A PROCESSING row without both lease fields cannot be recovered safely.
-- Prefer a possible duplicate over permanent message loss when repairing any
-- legacy inconsistent row.
UPDATE event_outbox
SET status = 'PENDING',
    processing_started_at = NULL,
    claim_token = NULL,
    published_at = NULL,
    updated_at = NOW(),
    version = version + 1
WHERE status = 'PROCESSING'
  AND (
      processing_started_at IS NULL
      OR claim_token IS NULL
      OR published_at IS NOT NULL
  );

UPDATE event_outbox
SET processing_started_at = NULL,
    claim_token = NULL,
    published_at = NULL,
    updated_at = NOW(),
    version = version + 1
WHERE status = 'PENDING'
  AND (
      processing_started_at IS NOT NULL
      OR claim_token IS NOT NULL
      OR published_at IS NOT NULL
  );

-- A row labelled PUBLISHED without a publication timestamp is ambiguous.
-- Requeueing preserves at-least-once delivery instead of silently losing it.
UPDATE event_outbox
SET status = 'PENDING',
    processing_started_at = NULL,
    claim_token = NULL,
    published_at = NULL,
    updated_at = NOW(),
    version = version + 1
WHERE status = 'PUBLISHED'
  AND published_at IS NULL;

UPDATE event_outbox
SET processing_started_at = NULL,
    claim_token = NULL,
    next_attempt_at = NULL,
    updated_at = NOW(),
    version = version + 1
WHERE status = 'PUBLISHED'
  AND published_at IS NOT NULL
  AND (
      processing_started_at IS NOT NULL
      OR claim_token IS NOT NULL
      OR next_attempt_at IS NOT NULL
  );

-- Keep the lease protocol valid even for manual SQL, future application code,
-- or bulk operations that bypass JPA entity validation.
ALTER TABLE event_outbox
    ADD CONSTRAINT ck_event_outbox_state_lease
        CHECK (
            (
                status = 'PENDING'
                AND processing_started_at IS NULL
                AND claim_token IS NULL
                AND published_at IS NULL
            )
            OR
            (
                status = 'PROCESSING'
                AND processing_started_at IS NOT NULL
                AND claim_token IS NOT NULL
                AND published_at IS NULL
            )
            OR
            (
                status = 'PUBLISHED'
                AND processing_started_at IS NULL
                AND claim_token IS NULL
                AND next_attempt_at IS NULL
                AND published_at IS NOT NULL
            )
        );
