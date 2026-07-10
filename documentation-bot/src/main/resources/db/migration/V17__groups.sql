-- Phase 8 — groups: bulk access grants instead of one-by-one per-user grants. Mirrors
-- document_access (V14) exactly, just keyed by group instead of user.
CREATE TABLE groups (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    name        VARCHAR(200) NOT NULL,
    created_by  UUID NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, name)
);
CREATE INDEX idx_groups_tenant ON groups(tenant_id);

CREATE TABLE group_memberships (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id    UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    added_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (group_id, user_id)
);
CREATE INDEX idx_group_memberships_group ON group_memberships(group_id);
CREATE INDEX idx_group_memberships_user ON group_memberships(user_id);

-- No FK to documents(id): same cross-service reasoning as document_access (V14) — that table is
-- owned by document-ingestor's own independent Flyway sequence with no ordering guarantee
-- relative to this service's migrations. Enforced at the application layer instead.
CREATE TABLE group_document_access (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id    UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    document_id UUID NOT NULL,
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    granted_by  UUID NOT NULL REFERENCES users(id),
    granted_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (group_id, document_id)
);
CREATE INDEX idx_group_document_access_group ON group_document_access(group_id);
CREATE INDEX idx_group_document_access_document ON group_document_access(document_id);
