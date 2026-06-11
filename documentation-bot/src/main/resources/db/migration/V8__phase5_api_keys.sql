-- V8: Phase 5 Integration Ecosystem — API Keys for programmatic access

CREATE TABLE IF NOT EXISTS api_keys (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    key_hash           VARCHAR(64)  NOT NULL UNIQUE,
    key_prefix         VARCHAR(12)  NOT NULL,          -- first 12 chars shown in UI for identification
    name               VARCHAR(100) NOT NULL,
    scopes             TEXT[]       NOT NULL DEFAULT '{"query"}',
    rate_limit_per_min INT          NOT NULL DEFAULT 60,
    last_used_at       TIMESTAMP,
    expires_at         TIMESTAMP,
    revoked            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_keys_user    ON api_keys(user_id);
CREATE INDEX idx_api_keys_hash    ON api_keys(key_hash);
CREATE INDEX idx_api_keys_prefix  ON api_keys(key_prefix);
