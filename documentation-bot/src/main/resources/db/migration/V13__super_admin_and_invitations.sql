-- Phase 1 of multi-tenancy overhaul: SUPER_ADMIN role + invite-based provisioning.

-- 'SUPER_ADMIN' (11 chars) no longer fits VARCHAR(10).
ALTER TABLE users ALTER COLUMN role TYPE VARCHAR(20);

-- SUPER_ADMIN is not scoped to any tenant.
ALTER TABLE users ALTER COLUMN tenant_id DROP NOT NULL;

CREATE TABLE invitation_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token       VARCHAR(100) NOT NULL UNIQUE,
    email       VARCHAR(100) NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    tenant_id   UUID REFERENCES tenants(id),
    invited_by  UUID NOT NULL REFERENCES users(id),
    expires_at  TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invitation_tokens_token ON invitation_tokens(token);
CREATE INDEX idx_invitation_tokens_email ON invitation_tokens(email);
