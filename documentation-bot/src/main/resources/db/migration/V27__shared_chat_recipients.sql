-- Named-recipient chat sharing: alongside the existing public/workspace-visibility link, a chat
-- owner can grant specific same-tenant users view access even while the link itself stays
-- non-public — mirrors the per-document access grant model (see document_access), applied here
-- to shared chats instead of documents.
CREATE TABLE shared_chat_recipients (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    link_id    UUID NOT NULL REFERENCES shared_chat_links(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    granted_by UUID NOT NULL REFERENCES users(id),
    granted_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (link_id, user_id)
);

CREATE INDEX idx_shared_chat_recipients_link ON shared_chat_recipients(link_id);
CREATE INDEX idx_shared_chat_recipients_user ON shared_chat_recipients(user_id);
