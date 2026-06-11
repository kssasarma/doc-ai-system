-- V7: Phase 4 Admin & Analytics Intelligence
-- Adds query logging, audit trail, and RBAC per product/version

-- Query logs: one row per user query for cost tracking and analytics
CREATE TABLE IF NOT EXISTS query_logs (
    id                  UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID             REFERENCES users(id) ON DELETE SET NULL,
    session_id          UUID             REFERENCES chat_sessions(id) ON DELETE SET NULL,
    question_preview    VARCHAR(200),
    product             VARCHAR(100),
    version             VARCHAR(50),
    confidence          DOUBLE PRECISION,
    latency_ms          INTEGER,
    prompt_tokens       INTEGER,
    completion_tokens   INTEGER,
    estimated_cost_usd  DOUBLE PRECISION,
    cited_documents     TEXT[],
    created_at          TIMESTAMP        NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_query_logs_user    ON query_logs(user_id);
CREATE INDEX idx_query_logs_created ON query_logs(created_at DESC);
CREATE INDEX idx_query_logs_product ON query_logs(product, version);

-- Audit log: immutable record of admin actions (SOC2/compliance foundation)
CREATE TABLE IF NOT EXISTS audit_log (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id        UUID         REFERENCES users(id) ON DELETE SET NULL,
    action          VARCHAR(100) NOT NULL,
    target_type     VARCHAR(50),
    target_id       UUID,
    metadata        TEXT,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_log_actor   ON audit_log(actor_id);
CREATE INDEX idx_audit_log_created ON audit_log(created_at DESC);
CREATE INDEX idx_audit_log_action  ON audit_log(action);

-- User product access: RBAC beyond binary ADMIN/USER roles
CREATE TABLE IF NOT EXISTS user_product_access (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product     VARCHAR(100) NOT NULL,
    version     VARCHAR(50),
    granted_by  UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
-- Separate partial indexes handle nullable version correctly
CREATE UNIQUE INDEX idx_product_access_specific
    ON user_product_access(user_id, product, version)
    WHERE version IS NOT NULL;
CREATE UNIQUE INDEX idx_product_access_all_versions
    ON user_product_access(user_id, product)
    WHERE version IS NULL;
CREATE INDEX idx_product_access_user ON user_product_access(user_id);
