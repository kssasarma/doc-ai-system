-- Database setup for doc-ai-system
-- Run this script to set up PostgreSQL with pgvector

-- Create database
CREATE DATABASE docai;

-- Connect to database
\c docai;

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Verify extension
SELECT * FROM pg_extension WHERE extname = 'vector';

-- The tables will be auto-created by Hibernate on first run
-- But you can create indexes manually for better performance:

-- Create vector index on document_chunks (after data is ingested)
-- Run this AFTER ingesting some documents:

-- For cosine similarity (recommended)
-- CREATE INDEX ON document_chunks USING ivfflat (embedding vector_cosine_ops)
--   WITH (lists = 100);

-- For L2 distance
-- CREATE INDEX ON document_chunks USING ivfflat (embedding vector_l2_ops)
--   WITH (lists = 100);

-- For HNSW (better performance, requires more memory)
-- CREATE INDEX ON document_chunks USING hnsw (embedding vector_cosine_ops);

PRINT 'Database setup complete!';
PRINT 'Remember to:';
PRINT '1. Set OPENAI_API_KEY environment variable (if required)';
PRINT '   export OPENAI_API_KEY=your-api-key';
PRINT '2. Start both services:';
PRINT '   - document-ingestor (port 8081)';
PRINT '   - documentation-bot (port 8082)';
