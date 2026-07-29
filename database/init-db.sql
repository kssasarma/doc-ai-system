-- Initialize pgvector extension for doc-ai-system.
-- documentation-bot and document-ingestor share this one database; neither uses Flyway's
-- baseline-on-migrate (see each service's application.yml), so both simply run their full V1+
-- migration chain against whatever schema exists here once the extension is installed.
CREATE EXTENSION IF NOT EXISTS vector;

-- Verify extension installation
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
