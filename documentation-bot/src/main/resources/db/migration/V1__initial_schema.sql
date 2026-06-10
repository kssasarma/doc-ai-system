-- V1: Initial schema for documentation-bot service
-- Note: documents and document_chunks tables are owned by the ingestor service.
-- This migration only creates tables owned by the bot service.

CREATE TABLE IF NOT EXISTS users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(50)  NOT NULL UNIQUE,
    email         VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(10)  NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS chat_sessions (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL,
    product        VARCHAR(100),
    version        VARCHAR(50),
    message_count  INTEGER     DEFAULT 0,
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW(),
    last_active_at TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id         UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id    UUID      NOT NULL,
    role       VARCHAR(20) NOT NULL,
    content    TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS chat_summaries (
    chat_id    UUID      PRIMARY KEY,
    summary    TEXT      NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_username      ON users(username);
CREATE INDEX IF NOT EXISTS idx_user_email         ON users(email);
CREATE INDEX IF NOT EXISTS idx_chat_session_user  ON chat_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_id            ON chat_messages(chat_id);
CREATE INDEX IF NOT EXISTS idx_chat_created       ON chat_messages(chat_id, created_at);
