ALTER TABLE notification
    DROP CONSTRAINT IF EXISTS ck_notification_priority;

ALTER TABLE notification
    ADD CONSTRAINT ck_notification_priority
        CHECK (priority IN ('NORMAL', 'IMPORTANT', 'CRITICAL'))
        NOT VALID;

-- Validation uses a weaker lock than adding an immediately-valid constraint,
-- allowing normal reads and writes to continue while PostgreSQL scans existing rows.
ALTER TABLE notification
    VALIDATE CONSTRAINT ck_notification_priority;
