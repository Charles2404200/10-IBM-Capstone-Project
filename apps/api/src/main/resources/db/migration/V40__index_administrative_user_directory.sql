-- Supports the default newest-first admin directory and optional access filters.
CREATE INDEX IF NOT EXISTS idx_users_created_at_desc ON users (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_users_role_active_created_at ON users (role, active, created_at DESC);
