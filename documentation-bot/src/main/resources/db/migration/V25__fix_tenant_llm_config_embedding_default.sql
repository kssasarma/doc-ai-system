-- V12 defaulted embedding_model to 'gpt-4o-embedding-4k' — not a real OpenAI model (same bug as
-- the OPENAI_EMBEDDING_MODEL env default fixed elsewhere). Any tenant that saved LLM config
-- before this fix, or any new row relying on the column default, would 404/400 on every
-- embedding call. Fix the default and backfill existing rows still holding the bad value.
ALTER TABLE tenant_llm_configs ALTER COLUMN embedding_model SET DEFAULT 'text-embedding-3-small';
UPDATE tenant_llm_configs SET embedding_model = 'text-embedding-3-small'
    WHERE embedding_model = 'gpt-4o-embedding-4k';
