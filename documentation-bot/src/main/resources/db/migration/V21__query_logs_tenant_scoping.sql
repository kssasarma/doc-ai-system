-- query_logs (Phase 4 analytics) predates tenancy (Phase 7) and was never retrofitted — every
-- Analytics/* dashboard query aggregated ALL tenants' query history and LLM spend together.
-- Same backfill pattern as V11: nullable first, fill from the owning user, then constrain.
ALTER TABLE query_logs ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);
UPDATE query_logs q SET tenant_id = u.tenant_id
    FROM users u WHERE q.user_id = u.id AND q.tenant_id IS NULL;
-- user_id is ON DELETE SET NULL, so a handful of rows may have no user to backfill from; those
-- are pre-existing orphaned log rows either way and stay NULL (application queries always filter
-- on a real tenant_id, so they simply become unreachable rather than misattributed).
CREATE INDEX idx_query_logs_tenant ON query_logs(tenant_id, created_at DESC);
