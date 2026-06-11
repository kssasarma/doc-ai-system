-- Phase 7.3 — Compliance (GDPR/SOC2 groundwork)

CREATE TABLE data_retention_policies (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    query_log_days      INT  NOT NULL DEFAULT 365,
    chat_session_days   INT  NOT NULL DEFAULT 730,
    audit_log_days      INT  NOT NULL DEFAULT 2555,  -- 7 years for SOC2
    feedback_days       INT  NOT NULL DEFAULT 365,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_retention_tenant UNIQUE (tenant_id)
);

-- Seed default retention for the default tenant
INSERT INTO data_retention_policies (tenant_id, query_log_days, chat_session_days, audit_log_days, feedback_days)
VALUES ('00000000-0000-0000-0000-000000000001', 365, 730, 2555, 365);

-- GDPR deletion requests
CREATE TABLE gdpr_deletion_requests (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants(id),
    user_id       UUID NOT NULL REFERENCES users(id),
    requested_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at  TIMESTAMP,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, COMPLETED, FAILED
    error_message TEXT
);
CREATE INDEX idx_gdpr_requests_tenant  ON gdpr_deletion_requests(tenant_id);
CREATE INDEX idx_gdpr_requests_user    ON gdpr_deletion_requests(user_id);
CREATE INDEX idx_gdpr_requests_status  ON gdpr_deletion_requests(status);

-- Phase 7.6 — Tenant Branding

CREATE TABLE tenant_branding (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
    product_name    VARCHAR(100) NOT NULL DEFAULT 'Docs-inator',
    logo_url        VARCHAR(500),
    favicon_url     VARCHAR(500),
    primary_color   VARCHAR(7)   NOT NULL DEFAULT '#2563eb',  -- Tailwind blue-600
    accent_color    VARCHAR(7)   NOT NULL DEFAULT '#7c3aed',  -- Tailwind violet-600
    custom_css      TEXT,
    support_email   VARCHAR(200),
    footer_text     VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Seed default branding
INSERT INTO tenant_branding (tenant_id, product_name, primary_color, accent_color)
VALUES ('00000000-0000-0000-0000-000000000001', 'Docs-inator', '#2563eb', '#7c3aed');

-- Phase 7.4 — Per-tenant LLM configuration

CREATE TABLE tenant_llm_configs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
    chat_provider     VARCHAR(50)  NOT NULL DEFAULT 'openai',  -- openai, anthropic, azure_openai
    chat_model        VARCHAR(100) NOT NULL DEFAULT 'gpt-4o-mini',
    embedding_provider VARCHAR(50) NOT NULL DEFAULT 'openai',
    embedding_model   VARCHAR(100) NOT NULL DEFAULT 'gpt-4o-embedding-4k',
    api_key_enc       TEXT,                         -- AES-256 encrypted API key override
    azure_endpoint    VARCHAR(500),                 -- for Azure OpenAI
    azure_deployment  VARCHAR(100),
    routing_enabled   BOOLEAN NOT NULL DEFAULT FALSE,  -- smart routing simple→cheap, complex→powerful
    simple_model      VARCHAR(100) DEFAULT 'gpt-4o-mini',
    complex_model     VARCHAR(100) DEFAULT 'gpt-4o',
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Seed default LLM config for default tenant
INSERT INTO tenant_llm_configs (tenant_id, chat_provider, chat_model, embedding_provider, embedding_model)
VALUES ('00000000-0000-0000-0000-000000000001', 'openai', 'gpt-4o-mini', 'openai', 'gpt-4o-embedding-4k');
