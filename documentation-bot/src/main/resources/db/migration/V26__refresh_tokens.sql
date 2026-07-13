-- Refresh tokens for silent session renewal — issued alongside the short-lived access JWT so the
-- frontend can renew a session without forcing re-login. Only the SHA-256 hash of the raw token
-- is stored (same principle as API keys): a DB leak alone can't be used to mint sessions.
-- No tenant_id column: the access token minted on rotation always reflects the user row's
-- *current* tenant_id/role (TenantMembershipService.switchActiveTenant persists both there), so a
-- silent renewal after a tenant switch in another tab naturally picks up the new context instead
-- of reissuing a stale one.
CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(64) NOT NULL UNIQUE,
    expires_at   TIMESTAMP NOT NULL,
    revoked_at   TIMESTAMP,
    replaced_by  UUID REFERENCES refresh_tokens(id) ON DELETE SET NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
