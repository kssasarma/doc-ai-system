-- Phase 2 of multi-tenancy overhaul: per-document, per-user access grants.
-- Replaces UserProductAccess (product/version-string grants, never actually enforced at
-- retrieval time) as the real, enforced eligibility gate for chat retrieval.
--
-- No FK to documents(id): that table is owned by document-ingestor's own independent Flyway
-- migration sequence, and the two services start (and migrate) concurrently with no ordering
-- guarantee between them — the same reason document-ingestor's own migrations never FK-reference
-- bot-owned tables like tenants(id) either. Referential correctness is enforced at the
-- application layer (DocumentAccessService validates the document exists and belongs to the
-- caller's tenant before creating a grant).
CREATE TABLE document_access (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    granted_by  UUID NOT NULL REFERENCES users(id),
    granted_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (document_id, user_id)
);

CREATE INDEX idx_document_access_user_tenant ON document_access(user_id, tenant_id);
CREATE INDEX idx_document_access_document ON document_access(document_id);
