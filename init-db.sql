-- Initialize pgvector extension for doc-ai-system
CREATE EXTENSION IF NOT EXISTS vector;

-- Verify extension installation
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';

-- documentation-bot and document-ingestor share this one database. Each has Flyway's
-- baseline-on-migrate enabled with a baseline-version calibrated for ONE specific pre-existing
-- deployment (see the comments in each service's application.yml) — it triggers whenever Flyway
-- finds its schema history table missing AND the schema non-empty. On a genuinely fresh
-- docker-compose volume, whichever of these two services' Flyway runs second sees the *other*
-- service's tables already sitting in the schema, incorrectly looks "non-empty/pre-existing" to
-- it, and silently skips that service's own early migrations (which the later ones depend on).
--
-- Pre-creating each service's schema history table here (empty, matching exactly what Flyway
-- itself would create) sidesteps the auto-baseline path entirely: Flyway only consults
-- baselineOnMigrate when the history table is *absent*. With it already present (holding zero
-- rows), both services just run their own full migration chain from V1, independent of start
-- order or what the other has already created.
CREATE TABLE IF NOT EXISTS flyway_schema_history_bot (
    installed_rank  INTEGER                     NOT NULL PRIMARY KEY,
    version         VARCHAR(50),
    description     VARCHAR(200)                NOT NULL,
    type            VARCHAR(20)                 NOT NULL,
    script          VARCHAR(1000)                NOT NULL,
    checksum        INTEGER,
    installed_by    VARCHAR(100)                NOT NULL,
    installed_on    TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    execution_time  INTEGER                     NOT NULL,
    success         BOOLEAN                     NOT NULL
);
CREATE INDEX IF NOT EXISTS flyway_schema_history_bot_s_idx ON flyway_schema_history_bot(success);

CREATE TABLE IF NOT EXISTS flyway_schema_history_ingestor (
    installed_rank  INTEGER                     NOT NULL PRIMARY KEY,
    version         VARCHAR(50),
    description     VARCHAR(200)                NOT NULL,
    type            VARCHAR(20)                 NOT NULL,
    script          VARCHAR(1000)                NOT NULL,
    checksum        INTEGER,
    installed_by    VARCHAR(100)                NOT NULL,
    installed_on    TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    execution_time  INTEGER                     NOT NULL,
    success         BOOLEAN                     NOT NULL
);
CREATE INDEX IF NOT EXISTS flyway_schema_history_ingestor_s_idx ON flyway_schema_history_ingestor(success);

-- You can add additional initialization here if needed
