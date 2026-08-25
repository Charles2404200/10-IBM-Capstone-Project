-- Existing users predate email confirmation and remain able to sign in. New
-- registrations explicitly persist email_verified = false until they use a
-- one-time credential sent to their inbox.
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMPTZ;

UPDATE users
SET email_verified_at = COALESCE(email_verified_at, created_at)
WHERE email_verified = TRUE;

CREATE TABLE IF NOT EXISTS email_verification_token (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    selector    VARCHAR(36) NOT NULL,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_email_verification_token_selector UNIQUE (selector)
);

CREATE INDEX IF NOT EXISTS idx_email_verification_token_active_user
    ON email_verification_token (user_id, expires_at)
    WHERE verified_at IS NULL AND revoked_at IS NULL;
