-- Existing verified accounts already used the product before account-scoped
-- onboarding existed. Keep their journeys uninterrupted while allowing only
-- newly registered learners to receive the first-use orientation.
ALTER TABLE users ADD COLUMN IF NOT EXISTS onboarding_completed_at TIMESTAMPTZ;

UPDATE users
SET onboarding_completed_at = COALESCE(onboarding_completed_at, email_verified_at, created_at)
WHERE onboarding_completed_at IS NULL
  AND email_verified = TRUE;
