ALTER TABLE notification
    ADD COLUMN message_preview VARCHAR(181);

UPDATE notification
SET message_preview = CASE
    WHEN char_length(message) > 180 THEN left(message, 179) || '…'
    ELSE message
END
WHERE message_preview IS NULL;

ALTER TABLE notification
    ALTER COLUMN message_preview SET NOT NULL;

CREATE INDEX idx_notification_role_created_event
    ON notification (role, created_at DESC, event_id DESC);

DROP INDEX idx_notification_role_created_at;

