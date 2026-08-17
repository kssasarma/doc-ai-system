-- The tenant-scoped session list query (findByUserIdAndTenantIdOrderByPinnedDescLastActiveAtDesc)
-- filters on both user_id and tenant_id, then sorts by pinned DESC, last_active_at DESC.
-- Separate single-column indexes on user_id and tenant_id cannot serve this efficiently;
-- a composite covering index eliminates the sort for the common case.
CREATE INDEX IF NOT EXISTS idx_chat_sessions_user_tenant
    ON chat_sessions(user_id, tenant_id, pinned DESC, last_active_at DESC);
