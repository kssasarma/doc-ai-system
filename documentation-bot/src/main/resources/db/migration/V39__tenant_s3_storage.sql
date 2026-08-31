-- Per-tenant S3 storage configuration. A tenant that has a row here uses their own S3 bucket and
-- credentials for document storage; tenants without a row fall back to the platform's default
-- storage (SeaweedFS in dev, a platform-managed S3 bucket in prod).
--
-- Access key and secret key are encrypted at rest with AES-256-GCM using the same
-- SECRETS_ENCRYPTION_KEY that protects per-tenant LLM API keys.
CREATE TABLE tenant_storage_configs (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID         NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
    s3_bucket           VARCHAR(200) NOT NULL,
    s3_region           VARCHAR(100) NOT NULL DEFAULT 'us-east-1',
    s3_access_key_enc   TEXT         NOT NULL,
    s3_secret_key_enc   TEXT         NOT NULL,
    -- null means real AWS S3; set for S3-compatible stores (MinIO, SeaweedFS, Backblaze B2, etc.)
    s3_endpoint         VARCHAR(500),
    s3_path_style_access BOOLEAN     NOT NULL DEFAULT false,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now()
);
