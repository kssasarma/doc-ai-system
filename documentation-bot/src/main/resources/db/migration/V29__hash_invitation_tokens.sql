-- Phase 4.3: invitation tokens were stored in plaintext (unlike refresh tokens/API keys, which
-- are hashed) — a DB read or log leak would yield directly usable invites. Any currently-pending
-- invitation is invalidated by this migration (recipients must be re-invited); given the existing
-- 72-hour expiry, this is an acceptable one-time cost for closing the gap rather than trying to
-- hash already-plaintext values into something meaningful.
ALTER TABLE invitation_tokens DROP COLUMN token;
ALTER TABLE invitation_tokens ADD COLUMN token_hash VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE invitation_tokens ALTER COLUMN token_hash DROP DEFAULT;

DROP INDEX IF EXISTS idx_invitation_tokens_token;
CREATE UNIQUE INDEX idx_invitation_tokens_token_hash ON invitation_tokens (token_hash);
