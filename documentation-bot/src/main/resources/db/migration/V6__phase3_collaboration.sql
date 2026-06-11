-- V6: Phase 3 Team Collaboration — shared links, collections, upvotes, escalations, annotations, notifications

-- Shared Chat Links: shareable read-only URLs for sessions
CREATE TABLE IF NOT EXISTS shared_chat_links (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id         UUID        NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    token           VARCHAR(36) UNIQUE NOT NULL,
    created_by      UUID        NOT NULL REFERENCES users(id),
    public_access   BOOLEAN     NOT NULL DEFAULT false,
    expires_at      TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_shared_links_token  ON shared_chat_links(token);
CREATE INDEX idx_shared_links_chat   ON shared_chat_links(chat_id);

-- Team Collections: curated sets of answers
CREATE TABLE IF NOT EXISTS collections (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    created_by      UUID        NOT NULL REFERENCES users(id),
    public_access   BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_collections_creator ON collections(created_by);

-- Collection Items: individual answers saved to a collection
CREATE TABLE IF NOT EXISTS collection_items (
    id                  UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_id       UUID    NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    chat_message_id     UUID    NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    chat_id             UUID    NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    note                TEXT,
    added_by            UUID    NOT NULL REFERENCES users(id),
    display_order       INT     NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(collection_id, chat_message_id)
);
CREATE INDEX idx_collection_items_collection ON collection_items(collection_id);

-- Answer Upvotes: community verification (3+ upvotes = "Team Verified")
CREATE TABLE IF NOT EXISTS answer_upvotes (
    id                  UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_message_id     UUID    NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    user_id             UUID    NOT NULL REFERENCES users(id),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(chat_message_id, user_id)
);
CREATE INDEX idx_upvotes_message ON answer_upvotes(chat_message_id);

-- Escalations: ask an expert when AI confidence is low
CREATE TABLE IF NOT EXISTS escalations (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_message_id     UUID        NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    question_text       TEXT        NOT NULL,
    ai_answer_text      TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_by          UUID        NOT NULL REFERENCES users(id),
    assigned_to         UUID        REFERENCES users(id),
    expert_answer       TEXT,
    product             VARCHAR(100),
    version             VARCHAR(50),
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    answered_at         TIMESTAMP
);
CREATE INDEX idx_escalations_status  ON escalations(status);
CREATE INDEX idx_escalations_creator ON escalations(created_by);

-- Chunk Annotations: inline commentary on source excerpts
CREATE TABLE IF NOT EXISTS chunk_annotations (
    id                  UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    document_chunk_id   UUID    NOT NULL,
    user_id             UUID    NOT NULL REFERENCES users(id),
    body                TEXT    NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_annotations_chunk ON chunk_annotations(document_chunk_id);

-- Notifications: in-app notification feed
CREATE TABLE IF NOT EXISTS notifications (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(id),
    type            VARCHAR(50) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    body            TEXT,
    reference_id    UUID,
    is_read         BOOLEAN     NOT NULL DEFAULT false,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, is_read);
