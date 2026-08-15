-- Password-reset credentials are deliberately stored as hashes. The raw OTP
-- and reset-token secret only leave the service in the email/API response.

CREATE TABLE password_reset_otp (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    otp_hash    VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0
);

-- Supports the repository query for a user's unconsumed, unrevoked OTPs.
CREATE INDEX idx_password_reset_otp_active_user
    ON password_reset_otp (user_id, expires_at)
    WHERE verified_at IS NULL AND revoked_at IS NULL;

CREATE TABLE password_reset_token (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    selector    VARCHAR(36) NOT NULL,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT idx_password_reset_token_selector UNIQUE (selector)
);

-- Supports bulk lookup/revocation of a user's currently active reset tokens.
CREATE INDEX idx_password_reset_token_active_user
    ON password_reset_token (user_id, expires_at)
    WHERE used_at IS NULL AND revoked_at IS NULL;
