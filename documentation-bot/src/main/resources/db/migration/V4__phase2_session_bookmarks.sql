-- V4: Phase 2 — session metadata (title, pin, tags) + bookmarks table

ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS title VARCHAR(200);
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS pinned BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS tags TEXT[] DEFAULT '{}';

CREATE INDEX IF NOT EXISTS idx_chat_session_pinned ON chat_sessions(user_id, pinned DESC, last_active_at DESC);

CREATE TABLE IF NOT EXISTS bookmarks (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL,
    chat_message_id UUID         NOT NULL,
    chat_id         UUID         NOT NULL,
    message_excerpt TEXT,
    title           VARCHAR(200),
    note            TEXT,
    tags            TEXT[]       DEFAULT '{}',
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bookmark_user    ON bookmarks(user_id);
CREATE INDEX IF NOT EXISTS idx_bookmark_message ON bookmarks(chat_message_id);
CREATE INDEX IF NOT EXISTS idx_bookmark_created ON bookmarks(user_id, created_at DESC);
