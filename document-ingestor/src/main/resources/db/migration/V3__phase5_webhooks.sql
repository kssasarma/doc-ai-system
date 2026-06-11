-- V3: Phase 5 — Webhook-based document ingestion tracking

CREATE TABLE IF NOT EXISTS webhook_events (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID         REFERENCES documents(id) ON DELETE SET NULL,
    download_url    TEXT         NOT NULL,
    product         VARCHAR(100) NOT NULL,
    version         VARCHAR(50)  NOT NULL,
    document_name   VARCHAR(255),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count     INT          NOT NULL DEFAULT 0,
    error_message   TEXT,
    requested_by    VARCHAR(100),     -- API key prefix or username
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMP
);

CREATE INDEX idx_webhook_events_status  ON webhook_events(status);
CREATE INDEX idx_webhook_events_created ON webhook_events(created_at DESC);
