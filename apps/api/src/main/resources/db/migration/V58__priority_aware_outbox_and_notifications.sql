ALTER TABLE event_outbox
    ADD COLUMN priority SMALLINT NOT NULL DEFAULT 200,
    ADD CONSTRAINT ck_event_outbox_priority
        CHECK (priority IN (100, 200, 300, 400));

CREATE INDEX idx_event_outbox_pending_priority_available
    ON event_outbox (priority DESC, next_attempt_at ASC, created_at ASC)
    WHERE status = 'PENDING';

ALTER TABLE notification
    ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    ADD CONSTRAINT ck_notification_priority
        CHECK (priority IN ('NORMAL', 'CRITICAL'));
