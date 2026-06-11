-- Phase 6: Semantic Chunking v2
-- Add metadata columns to support hierarchical, boundary-aware chunking

ALTER TABLE document_chunks
    ADD COLUMN IF NOT EXISTS chunk_type      VARCHAR(50)  DEFAULT 'TEXT',
    ADD COLUMN IF NOT EXISTS parent_chunk_id UUID         REFERENCES document_chunks(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS section_header  TEXT,
    ADD COLUMN IF NOT EXISTS page_number     INT,
    ADD COLUMN IF NOT EXISTS code_language   VARCHAR(50),
    ADD COLUMN IF NOT EXISTS is_leaf         BOOLEAN      NOT NULL DEFAULT TRUE;

CREATE INDEX idx_chunks_type_doc ON document_chunks(document_id, chunk_type);
CREATE INDEX idx_chunks_parent   ON document_chunks(parent_chunk_id) WHERE parent_chunk_id IS NOT NULL;
CREATE INDEX idx_chunks_leaf     ON document_chunks(document_id, is_leaf) WHERE is_leaf = TRUE;
