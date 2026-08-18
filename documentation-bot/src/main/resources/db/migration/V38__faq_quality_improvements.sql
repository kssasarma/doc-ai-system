-- FAQ quality improvements: review_note column, semantic-clustering dedup indexes, and
-- helpfulness-rate feedback-loop index.

-- Audit trail for auto-flagged (stale) entries and admin edits: set by FaqMaintenanceService
-- when an approved FAQ's helpfulness rate falls below the threshold, and cleared by the admin
-- on re-approval.
ALTER TABLE faq_entries ADD COLUMN IF NOT EXISTS review_note TEXT;

-- Rolling-window dedup: covers FaqClusterRepository.findRecentClusters (tenant + product +
-- version + period_end range). The existing idx_faq_clusters_tenant index (added in V11)
-- is a single-column index; replace it with a composite that still covers tenant-only lookups
-- as its leading column while also serving the period_end range scan without a seq scan.
DROP INDEX IF EXISTS idx_faq_clusters_tenant;
CREATE INDEX IF NOT EXISTS idx_faq_clusters_tenant_rolling
    ON faq_clusters(tenant_id, product, version, period_end DESC);

-- Rejected-entry dedup: covers the query that blocks regeneration of topics an admin already
-- rejected. Also covers the per-tenant rejected-entry listing used by the admin pending queue.
CREATE INDEX IF NOT EXISTS idx_faq_entries_tenant_product_status_created
    ON faq_entries(tenant_id, product, version, status, created_at DESC);

-- Helpfulness feedback loop: partial index on APPROVED entries for FaqMaintenanceService's
-- stale scan (findApprovedWithMinViews). Partial because only APPROVED rows are ever scanned.
CREATE INDEX IF NOT EXISTS idx_faq_entries_approved_views
    ON faq_entries(view_count, helpful_count)
    WHERE status = 'APPROVED';
