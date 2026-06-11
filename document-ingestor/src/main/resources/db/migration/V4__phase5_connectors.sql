-- Integration tokens for Confluence and Notion connectors
CREATE TABLE integration_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider        VARCHAR(30) NOT NULL,          -- 'confluence' | 'notion'
    access_token    TEXT NOT NULL,
    refresh_token   TEXT,
    token_expires_at TIMESTAMPTZ,
    site_url        VARCHAR(500),                  -- Confluence base URL (e.g. https://myorg.atlassian.net)
    workspace_id    VARCHAR(100),                  -- Notion workspace id
    workspace_name  VARCHAR(200),
    scopes          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_integration_tokens_user_provider ON integration_tokens(user_id, provider);

-- Track which pages/documents have been synced so we can detect changes
CREATE TABLE connector_sync_pages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_id        UUID NOT NULL REFERENCES integration_tokens(id) ON DELETE CASCADE,
    document_id     UUID REFERENCES documents(id) ON DELETE SET NULL,
    provider        VARCHAR(30) NOT NULL,
    external_id     VARCHAR(200) NOT NULL,         -- Confluence pageId or Notion pageId
    title           VARCHAR(500),
    space_key       VARCHAR(100),                  -- Confluence space key
    last_synced_at  TIMESTAMPTZ,
    last_modified   TIMESTAMPTZ,                   -- remote last_modified at time of last sync
    sync_status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_sync_pages_token_external ON connector_sync_pages(token_id, external_id);
CREATE INDEX idx_sync_pages_status ON connector_sync_pages(sync_status);
