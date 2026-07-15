-- Phase 1.5 — records which embedding model/provider actually produced a document's chunk
-- embeddings, so query-time embedding (bot side, Phase 1.4) can request the SAME model for a
-- given document rather than blindly using the tenant's *current* embedding config, which may
-- have changed since this document was ingested.
ALTER TABLE documents ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(100);
