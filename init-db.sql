-- Initialize pgvector extension for doc-ai-system
CREATE EXTENSION IF NOT EXISTS vector;

-- Verify extension installation
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';

-- You can add additional initialization here if needed
