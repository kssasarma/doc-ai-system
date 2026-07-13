-- answer_feedback (Phase 1) predates tenancy (Phase 7) and was never retrofitted — needed now so
-- AnalyticsService.getOverview can count positive/negative feedback within a single tenant.
-- Same backfill pattern as V11/V21: nullable first, fill from the owning user, then constrain.
ALTER TABLE answer_feedback ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);
UPDATE answer_feedback f SET tenant_id = u.tenant_id
    FROM users u WHERE f.user_id = u.id AND f.tenant_id IS NULL;
ALTER TABLE answer_feedback ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_feedback_tenant ON answer_feedback(tenant_id, rating);
