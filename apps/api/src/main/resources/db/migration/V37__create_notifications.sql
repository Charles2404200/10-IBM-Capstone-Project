CREATE TABLE notification (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id),
    message     TEXT NOT NULL,
    role        VARCHAR(30) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_notification_user_id ON notification (user_id);
