-- Lets a tenant point the re-rank pass (ReRankingService's LLM relevance re-rank, distinct from
-- answer generation) at its own model, independent of the simple/complex routing split. NULL
-- (the default) means "inherit" — fall back to the simple-queries model when routing is enabled,
-- else the tenant's single chat model — so existing tenants keep today's behavior unchanged.
ALTER TABLE tenant_llm_configs ADD COLUMN rerank_model VARCHAR(100);
