-- Completes the GDPR Article 17 pipeline: previously nothing ever processed a deletion request
-- (see GdprErasureService). deleted_at marks an erased account so login is refused even though
-- the row itself is kept (its id stays valid on shared/collaborative content elsewhere —
-- collections, groups, escalations, chunk annotations, FAQ approvals — that must not be
-- destroyed just because one contributor was erased).
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
