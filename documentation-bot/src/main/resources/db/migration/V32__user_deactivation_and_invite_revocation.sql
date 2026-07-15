-- Phase 6.4 — lightweight, reversible account deactivation distinct from GDPR erasure (deleted_at),
-- and the ability to revoke a still-pending invitation before it's accepted.
ALTER TABLE users ADD COLUMN deactivated_at TIMESTAMP NULL;
ALTER TABLE invitation_tokens ADD COLUMN revoked_at TIMESTAMP NULL;
