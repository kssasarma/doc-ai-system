-- Per-tenant override for the ingestor's embedding batch token ceiling. Tenants can bring their
-- own embedding model (see embedding_provider/embedding_model on this table), and different models
-- have different context windows (e.g. snowflake-arctic caps at 8192 tokens per request, summed
-- across every input in the batch) — a single platform-wide limit can't fit every tenant's model.
-- NULL means "use the ingestor's platform-default limit".
ALTER TABLE tenant_llm_configs ADD COLUMN max_embedding_batch_tokens INTEGER;
