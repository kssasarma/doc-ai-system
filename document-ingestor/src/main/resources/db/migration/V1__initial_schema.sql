-- V1: Initial schema for document-ingestor service
-- Prerequisite: pgvector extension must be installed (handled by init-db.sql / docker entrypoint)

CREATE TABLE IF NOT EXISTS documents (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    product      VARCHAR(100) NOT NULL,
    version      VARCHAR(50)  NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    file_path    VARCHAR(500),
    file_hash    VARCHAR(64)  NOT NULL,
    file_type    VARCHAR(10),
    chunk_count  INTEGER      DEFAULT 0,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS document_chunks (
    id           UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID    NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index  INTEGER NOT NULL,
    content      TEXT    NOT NULL,
    embedding    vector(1024),
    token_count  INTEGER,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_product_version  ON documents(product, version);
CREATE INDEX IF NOT EXISTS idx_file_hash        ON documents(file_hash);
CREATE INDEX IF NOT EXISTS idx_document_id      ON document_chunks(document_id);
CREATE INDEX IF NOT EXISTS idx_chunk_index      ON document_chunks(document_id, chunk_index);
