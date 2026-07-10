-- pgvector is only ever enabled via a manual step in SETUP.md for real deployments;
-- nothing does it for a fresh Testcontainers database, so integration tests using vector
-- columns (DocumentChunk.embedding) need it created explicitly before schema creation runs.
CREATE EXTENSION IF NOT EXISTS vector;
