-- V2: HNSW index on document_chunks (shared table, owned by ingestor)
-- Using IF NOT EXISTS: the ingestor service may have already created this index.
-- Both services need this covered so whichever starts first wins.

CREATE INDEX IF NOT EXISTS idx_chunk_embedding_hnsw
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
