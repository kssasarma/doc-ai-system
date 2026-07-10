CREATE TABLE tenant_memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, tenant_id)
);
CREATE INDEX idx_tenant_memberships_user ON tenant_memberships(user_id);
CREATE INDEX idx_tenant_memberships_tenant ON tenant_memberships(tenant_id);

-- Backfill: every existing tenant-scoped user (tenant_id IS NOT NULL — i.e. every role except
-- SUPER_ADMIN, which is not scoped to any tenant) gets a membership row mirroring the single
-- tenant/role they hold today. users.tenant_id/role remain on the table as the "active" tenant
-- cache (see User entity Javadoc) rather than being dropped — most read sites (JWT issuance,
-- ApiKeyAuthFilter, audit exports) want "the tenant this session is currently acting as," which
-- this column continues to serve; tenant_memberships is the new source of truth for "which
-- tenants can this identity switch into."
INSERT INTO tenant_memberships (user_id, tenant_id, role, joined_at)
SELECT id, tenant_id, role, created_at FROM users WHERE tenant_id IS NOT NULL;
