ALTER TABLE event_outbox
    ADD COLUMN failed_at TIMESTAMPTZ,
    ADD COLUMN last_error TEXT;

ALTER TABLE event_outbox
    DROP CONSTRAINT ck_event_outbox_state_lease;

-- FAILED is terminal and deliberately remains in the table for investigation.
-- It also remains an ordering blocker so later events for the same ordering key
-- cannot overtake a failed predecessor silently.
ALTER TABLE event_outbox
    ADD CONSTRAINT ck_event_outbox_state_lease
        CHECK (
            (
                status = 'PENDING'
                AND processing_started_at IS NULL
                AND claim_token IS NULL
                AND published_at IS NULL
                AND failed_at IS NULL
                AND last_error IS NULL
            )
            OR
            (
                status = 'PROCESSING'
                AND processing_started_at IS NOT NULL
                AND claim_token IS NOT NULL
                AND published_at IS NULL
                AND failed_at IS NULL
                AND last_error IS NULL
            )
            OR
            (
                status = 'PUBLISHED'
                AND processing_started_at IS NULL
                AND claim_token IS NULL
                AND next_attempt_at IS NULL
                AND published_at IS NOT NULL
                AND failed_at IS NULL
                AND last_error IS NULL
            )
            OR
            (
                status = 'FAILED'
                AND processing_started_at IS NULL
                AND claim_token IS NULL
                AND next_attempt_at IS NULL
                AND published_at IS NULL
                AND failed_at IS NOT NULL
                AND last_error IS NOT NULL
                AND char_length(last_error) BETWEEN 1 AND 1000
                AND attempt_count > 0
            )
        );
