-- Chunk-level PII review: previously an admin could only quarantine/release/reject a whole
-- document over PII findings. This lets an admin exclude a single offending chunk from search
-- while leaving the rest of the document (and any other chunks) searchable.
ALTER TABLE document_chunks ADD COLUMN excluded_from_search BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE document_chunks ADD COLUMN exclusion_reason VARCHAR(50);

CREATE INDEX idx_document_chunks_excluded ON document_chunks (excluded_from_search)
    WHERE excluded_from_search = TRUE;

-- PiiFlag rows created by a per-chunk scan (see PiiDetectionService#scanChunksAndFlag) carry the
-- offending chunk's id so admins can act on that chunk specifically; flags from the pre-chunking
-- whole-document scan (which drives document-level quarantine) leave this null.
ALTER TABLE pii_flags ADD COLUMN document_chunk_id UUID REFERENCES document_chunks (id) ON DELETE CASCADE;

CREATE INDEX idx_pii_flags_chunk ON pii_flags (document_chunk_id);
