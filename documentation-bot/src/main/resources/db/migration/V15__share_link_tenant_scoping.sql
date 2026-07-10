-- Phase 5 — chat-sharing security fix.
-- shared_chat_links had no tenant_id, so SharedChatService could never check the viewer's
-- tenant against a non-public link even if it wanted to. Backfill from the owning session.
ALTER TABLE shared_chat_links ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);

UPDATE shared_chat_links scl SET tenant_id = cs.tenant_id
    FROM chat_sessions cs
    WHERE scl.chat_id = cs.id AND scl.tenant_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_shared_chat_links_tenant ON shared_chat_links(tenant_id);
