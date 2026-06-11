-- Email digest subscriptions
CREATE TABLE email_digests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    frequency       VARCHAR(20) NOT NULL DEFAULT 'WEEKLY',   -- DAILY | WEEKLY | MONTHLY
    send_day        SMALLINT,                                 -- 1-7 (MON-SUN) for WEEKLY; 1-28 for MONTHLY
    send_hour       SMALLINT NOT NULL DEFAULT 8,             -- 0-23 UTC
    product_filter  VARCHAR(100),                            -- NULL = all products
    version_filter  VARCHAR(50),
    last_sent_at    TIMESTAMPTZ,
    next_send_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id)
);

CREATE INDEX idx_email_digests_next_send ON email_digests(next_send_at) WHERE enabled = TRUE;
