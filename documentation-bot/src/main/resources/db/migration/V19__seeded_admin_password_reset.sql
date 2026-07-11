-- Self-service bootstrap is gone: the first SUPER_ADMIN is now seeded at application startup
-- (see AdminSeeder) instead of created via a public /api/auth/bootstrap endpoint. Seeded accounts
-- must change their password on first login.
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT false;
