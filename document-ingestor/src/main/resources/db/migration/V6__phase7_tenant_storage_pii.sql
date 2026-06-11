-- Phase 7.1 — Tenant isolation on documents
-- Phase 7.3 — PII detection flags
-- Phase 7.5 — Storage type metadata

ALTER TABLE documents ADD COLUMN IF NOT EXISTS tenant_id UUID;
UPDATE documents SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
ALTER TABLE documents ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_documents_tenant ON documents(tenant_id);

ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS tenant_id UUID;
UPDATE document_chunks dc SET tenant_id = d.tenant_id
    FROM documents d WHERE dc.document_id = d.id;
CREATE INDEX idx_chunks_tenant ON document_chunks(tenant_id);

-- Storage location metadata
ALTER TABLE documents ADD COLUMN IF NOT EXISTS storage_type  VARCHAR(20) DEFAULT 'LOCAL';  -- LOCAL, S3, GCS
ALTER TABLE documents ADD COLUMN IF NOT EXISTS storage_key   VARCHAR(1000);  -- S3 object key or GCS path
ALTER TABLE documents ADD COLUMN IF NOT EXISTS storage_bucket VARCHAR(200);

-- Phase 7.3 — PII Detection
CREATE TABLE pii_flags (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id   UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    tenant_id     UUID NOT NULL,
    pii_type      VARCHAR(50) NOT NULL,   -- EMAIL, PHONE, SSN, CREDIT_CARD, IP_ADDRESS
    occurrence_count INT NOT NULL DEFAULT 0,
    sample_excerpt VARCHAR(200),          -- redacted sample for admin review
    risk_level    VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',  -- LOW, MEDIUM, HIGH, CRITICAL
    reviewed      BOOLEAN NOT NULL DEFAULT FALSE,
    reviewed_by   UUID,
    reviewed_at   TIMESTAMP,
    action_taken  VARCHAR(50),            -- APPROVED, REDACTED, BLOCKED
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_pii_flags_document ON pii_flags(document_id);
CREATE INDEX idx_pii_flags_tenant   ON pii_flags(tenant_id);
CREATE INDEX idx_pii_flags_reviewed ON pii_flags(reviewed) WHERE reviewed = FALSE;
