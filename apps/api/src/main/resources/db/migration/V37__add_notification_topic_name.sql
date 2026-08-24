ALTER TABLE notification
    ADD COLUMN topic_name VARCHAR(160);

-- Notifications created before headings were introduced retain a clear,
-- non-empty display heading without deriving it from the message body.
UPDATE notification
SET topic_name = 'Notification'
WHERE topic_name IS NULL;

ALTER TABLE notification
    ALTER COLUMN topic_name SET NOT NULL;
