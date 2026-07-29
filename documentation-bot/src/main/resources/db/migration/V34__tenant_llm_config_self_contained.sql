-- Tenant AI config becomes fully self-contained: every AI-related setting (endpoints, keys,
-- generation options) now lives on this table and is managed by the tenant's admin in the UI.
-- There is no platform-level fallback key/endpoint anymore (OPENAI_API_KEY & co. were removed
-- from docker-compose/application.yml), so AI features stay off for a tenant until its admin
-- saves a working configuration.

-- Provider endpoints. NULL means the provider's canonical public endpoint
-- (https://api.openai.com / https://api.anthropic.com); set for Azure OpenAI, proxies, or any
-- OpenAI-compatible self-hosted gateway.
ALTER TABLE tenant_llm_configs ADD COLUMN chat_base_url VARCHAR(500);
ALTER TABLE tenant_llm_configs ADD COLUMN embedding_base_url VARCHAR(500);

-- Separate embedding key (AES-256-GCM, same scheme as api_key_enc). Needed when chat and
-- embedding use different providers (e.g. Anthropic chat + OpenAI embeddings). NULL falls back
-- to api_key_enc only when embedding_provider equals chat_provider — never to a platform key.
ALTER TABLE tenant_llm_configs ADD COLUMN embedding_api_key_enc TEXT;

-- Chat generation options, previously hard-coded platform-wide in application.yml
-- (spring.ai.openai.chat.options.temperature / max-tokens).
ALTER TABLE tenant_llm_configs ADD COLUMN temperature DOUBLE PRECISION NOT NULL DEFAULT 0.7;
ALTER TABLE tenant_llm_configs ADD COLUMN max_tokens INTEGER NOT NULL DEFAULT 5000;
