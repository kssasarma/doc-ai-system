-- Phase 1 of multi-tenancy overhaul: connectors (Confluence/Notion) need a tenant to attribute
-- synced documents to, same as documents.tenant_id added in V6.

ALTER TABLE integration_tokens ADD COLUMN IF NOT EXISTS tenant_id UUID;

-- Backfill from the connecting user's tenant (same physical database as documentation-bot).
UPDATE integration_tokens it SET tenant_id = u.tenant_id
    FROM users u WHERE it.user_id = u.id AND it.tenant_id IS NULL;

CREATE INDEX idx_integration_tokens_tenant ON integration_tokens(tenant_id);

-- Webhook ingestion also needs a tenant, captured synchronously (on the request thread, from the
-- caller's JWT) at event-creation time — processEvent() runs @Async on a different thread, where
-- the request's ThreadLocal TenantContext is not available.
ALTER TABLE webhook_events ADD COLUMN IF NOT EXISTS tenant_id UUID;
CREATE INDEX idx_webhook_events_tenant ON webhook_events(tenant_id);
