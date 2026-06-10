-- V2: HNSW index for fast approximate nearest-neighbour vector search
-- Without this, every similarity query is a full sequential scan — unusable beyond ~50k chunks.
-- m=16 / ef_construction=64 is a good balance of build time vs. query accuracy.
-- This index build may take a few minutes on large existing tables.

CREATE INDEX IF NOT EXISTS idx_chunk_embedding_hnsw
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
