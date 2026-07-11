-- V2: HNSW index on document_chunks (shared table, owned by ingestor)
-- Using IF NOT EXISTS: the ingestor service may have already created this index.
-- Both services need this covered so whichever starts first wins. On a fresh database,
-- document_chunks may not exist yet if this service's Flyway runs before the ingestor's own V1 —
-- that's fine, document-ingestor's identical V2 creates the index once that table exists.
DO $$
BEGIN
    IF to_regclass('public.document_chunks') IS NOT NULL THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_chunk_embedding_hnsw '
            || 'ON document_chunks USING hnsw (embedding vector_cosine_ops) '
            || 'WITH (m = 16, ef_construction = 64)';
    END IF;
END $$;
