-- Achievement/gamification module: admin-authored unlock rules (composable AND/OR
-- condition trees) plus a per-user unlock ledger.
CREATE TABLE achievements (
    id          UUID PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    icon_key    VARCHAR(50) NOT NULL DEFAULT 'trophy',
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    rule_json   TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE user_achievements (
    id             UUID PRIMARY KEY,
    user_id        UUID NOT NULL REFERENCES users(id),
    achievement_id UUID NOT NULL REFERENCES achievements(id),
    unlocked_at    TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    version        BIGINT NOT NULL DEFAULT 0,
    UNIQUE (user_id, achievement_id)
);

CREATE INDEX idx_achievements_active ON achievements (active);
CREATE INDEX idx_user_achievements_user ON user_achievements (user_id);
