-- Phase 7.1 — Multi-Tenancy (row-level isolation)

CREATE TABLE tenants (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(200) NOT NULL,
    slug           VARCHAR(100) NOT NULL UNIQUE,
    plan           VARCHAR(50)  NOT NULL DEFAULT 'FREE',  -- FREE, PRO, ENTERPRISE
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    max_users      INT          NOT NULL DEFAULT 10,
    max_documents  INT          NOT NULL DEFAULT 100,
    oidc_enabled   BOOLEAN      NOT NULL DEFAULT FALSE,
    oidc_provider  VARCHAR(100),          -- google, azure, okta, auth0, custom
    oidc_issuer    VARCHAR(500),
    oidc_client_id VARCHAR(200),
    saml_enabled   BOOLEAN      NOT NULL DEFAULT FALSE,
    saml_idp_url   VARCHAR(500),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Seed a default tenant so existing users are not orphaned
INSERT INTO tenants (id, name, slug, plan, max_users, max_documents)
VALUES ('00000000-0000-0000-0000-000000000001', 'Default', 'default', 'ENTERPRISE', 9999, 99999);

-- Add tenant_id to users (nullable first, fill, then constrain)
ALTER TABLE users ADD COLUMN tenant_id UUID REFERENCES tenants(id);
UPDATE users SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
ALTER TABLE users ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE users ADD COLUMN oidc_sub VARCHAR(500);
ALTER TABLE users ADD COLUMN oidc_provider VARCHAR(100);
ALTER TABLE users ADD COLUMN display_name VARCHAR(200);
ALTER TABLE users ADD COLUMN avatar_url VARCHAR(500);

CREATE INDEX idx_users_tenant ON users(tenant_id);
CREATE INDEX idx_users_oidc_sub ON users(oidc_sub) WHERE oidc_sub IS NOT NULL;

-- Add tenant_id to chat_sessions
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);
UPDATE chat_sessions cs SET tenant_id = u.tenant_id
    FROM users u WHERE cs.user_id = u.id;
CREATE INDEX idx_chat_sessions_tenant ON chat_sessions(tenant_id);

-- Add tenant_id to bookmarks
ALTER TABLE bookmarks ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);
UPDATE bookmarks b SET tenant_id = u.tenant_id
    FROM users u WHERE b.user_id = u.id;
CREATE INDEX idx_bookmarks_tenant ON bookmarks(tenant_id);

-- Add tenant_id to collections
ALTER TABLE collections ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);
UPDATE collections c SET tenant_id = u.tenant_id
    FROM users u WHERE c.created_by = u.id;
CREATE INDEX idx_collections_tenant ON collections(tenant_id);

-- Add tenant_id to api_keys
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);
UPDATE api_keys ak SET tenant_id = u.tenant_id
    FROM users u WHERE ak.user_id = u.id;
CREATE INDEX idx_api_keys_tenant ON api_keys(tenant_id);

-- Add tenant_id to document_gap_reports (Phase 6)
ALTER TABLE documentation_gap_reports ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);
UPDATE documentation_gap_reports SET tenant_id = '00000000-0000-0000-0000-000000000001'
    WHERE tenant_id IS NULL;
CREATE INDEX idx_gap_reports_tenant ON documentation_gap_reports(tenant_id);

-- Add tenant_id to faq_entries and faq_clusters (Phase 6)
ALTER TABLE faq_entries  ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);
ALTER TABLE faq_clusters ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);
UPDATE faq_entries  SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
UPDATE faq_clusters SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
CREATE INDEX idx_faq_entries_tenant  ON faq_entries(tenant_id);
CREATE INDEX idx_faq_clusters_tenant ON faq_clusters(tenant_id);

-- Add tenant_id to topic_subscriptions (Phase 6)
ALTER TABLE topic_subscriptions ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);
UPDATE topic_subscriptions ts SET tenant_id = u.tenant_id
    FROM users u WHERE ts.user_id = u.id;
CREATE INDEX idx_topic_subscriptions_tenant ON topic_subscriptions(tenant_id);
