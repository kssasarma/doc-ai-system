-- Phase 7 — UserPreference.defaultProduct/defaultVersion was write-only: PreferencesModal set it,
-- nothing in the query path ever read it (the chat request's own product/version — now a real,
-- opt-in scope narrow — superseded this). Dropping rather than leaving an unused column behind.
ALTER TABLE user_preferences DROP COLUMN IF EXISTS default_product;
ALTER TABLE user_preferences DROP COLUMN IF EXISTS default_version;
