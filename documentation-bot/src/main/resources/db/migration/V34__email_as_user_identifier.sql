-- V34: Email is the sole user-facing identifier.
--
-- The separate username login identifier is retired: users log in with their email, JWTs carry
-- an "email" claim instead of "username", and every API surface that displayed a username now
-- shows the email (or display name). The UUID users.id stays as a purely internal surrogate key
-- for foreign-key references — it is never chosen by or shown to users.
--
-- Dropping the column also drops its NOT NULL/UNIQUE constraints and any dependent index, but
-- the V1 index is dropped explicitly for clarity. No data migration is needed: email is already
-- NOT NULL UNIQUE since V1, so every existing account can log in with its email immediately.

DROP INDEX IF EXISTS idx_user_username;
ALTER TABLE users DROP COLUMN IF EXISTS username;
