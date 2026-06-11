-- Phase 6: Intelligence Upgrades

-- ── FAQ Generation ────────────────────────────────────────────────────────────

CREATE TABLE faq_clusters (
    id                UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    product           VARCHAR(100),
    version           VARCHAR(50),
    canonical_question TEXT   NOT NULL,
    query_count       INT     NOT NULL DEFAULT 0,
    unique_users      INT     NOT NULL DEFAULT 0,
    period_start      DATE    NOT NULL,
    period_end        DATE    NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE faq_entries (
    id            UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_id    UUID    REFERENCES faq_clusters(id) ON DELETE CASCADE,
    question      TEXT    NOT NULL,
    answer        TEXT    NOT NULL,
    product       VARCHAR(100),
    version       VARCHAR(50),
    sources       JSONB,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | APPROVED | REJECTED
    approved_by   UUID    REFERENCES users(id),
    approved_at   TIMESTAMPTZ,
    view_count    INT     NOT NULL DEFAULT 0,
    helpful_count INT     NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_faq_entries_product_status ON faq_entries(product, version, status);
CREATE INDEX idx_faq_entries_status_created ON faq_entries(status, created_at DESC);
CREATE INDEX idx_faq_clusters_product       ON faq_clusters(product, version, period_end DESC);

-- ── Proactive Topic Subscriptions ────────────────────────────────────────────

CREATE TABLE topic_subscriptions (
    id         UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    topic      TEXT    NOT NULL,
    product    VARCHAR(100),
    version    VARCHAR(50),           -- NULL = all versions
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_topic_subs_user    ON topic_subscriptions(user_id);
CREATE INDEX idx_topic_subs_product ON topic_subscriptions(product, version);

-- ── Documentation Gap Reports ─────────────────────────────────────────────────

CREATE TABLE documentation_gap_reports (
    id                           UUID  PRIMARY KEY DEFAULT gen_random_uuid(),
    product                      VARCHAR(100),
    version                      VARCHAR(50),
    report_period_start          DATE  NOT NULL,
    report_period_end            DATE  NOT NULL,
    total_low_confidence_queries INT   NOT NULL DEFAULT 0,
    gap_topics                   JSONB NOT NULL DEFAULT '[]',
    generated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    exported_at                  TIMESTAMPTZ
);

CREATE INDEX idx_gap_reports_product ON documentation_gap_reports(product, version, report_period_end DESC);

-- ── Query Session Graph ("People Also Asked") ─────────────────────────────────

CREATE TABLE query_session_graph (
    id         UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID    NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    user_id    UUID    REFERENCES users(id) ON DELETE SET NULL,
    query_text TEXT    NOT NULL,
    product    VARCHAR(100),
    version    VARCHAR(50),
    asked_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_qsg_session ON query_session_graph(session_id, asked_at);
CREATE INDEX idx_qsg_product ON query_session_graph(product, version, asked_at DESC);
