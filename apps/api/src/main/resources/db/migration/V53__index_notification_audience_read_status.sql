-- Supports the bounded administrator audience/read-status count and keyset
-- page without indexing inactive accounts that cannot receive notifications.
CREATE INDEX idx_users_active_role_display_name_id
    ON users (role, LOWER(display_name), id)
    WHERE active = TRUE;
