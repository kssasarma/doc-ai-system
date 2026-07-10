-- Phase 6 — hybrid retrieval. A pure dense (cosine-similarity) search under-ranks short,
-- specific factual queries (e.g. "supported application servers") when the matching chunk's
-- embedding gets diluted by surrounding unrelated text. Add a lexical full-text path alongside
-- the existing pgvector index so retrieval fuses both signals instead of relying on dense alone.
ALTER TABLE document_chunks
    ADD COLUMN IF NOT EXISTS search_vector tsvector
        GENERATED ALWAYS AS (to_tsvector('english', content)) STORED;

CREATE INDEX IF NOT EXISTS idx_document_chunks_search_vector
    ON document_chunks USING GIN (search_vector);
