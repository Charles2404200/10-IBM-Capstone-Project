ALTER TABLE notification
    ADD COLUMN event_id UUID;

-- Existing rows predate producer event ids; their entity id is a stable unique
-- fallback that allows the new column to become non-null safely.
UPDATE notification
SET event_id = id
WHERE event_id IS NULL;

ALTER TABLE notification
    ALTER COLUMN event_id SET NOT NULL,
    ADD CONSTRAINT uk_notification_event_id UNIQUE (event_id);

CREATE TABLE notification_reads (
    id              UUID PRIMARY KEY,
    notification_id UUID NOT NULL REFERENCES notification(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    read_at         TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_notification_reads_notification_user UNIQUE (notification_id, user_id)
);

CREATE INDEX idx_notification_role_created_at
    ON notification (role, created_at DESC);

CREATE INDEX idx_notification_reads_user
    ON notification_reads (user_id, notification_id);
