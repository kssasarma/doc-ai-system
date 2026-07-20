# Database

Both services share a single PostgreSQL 16 + pgvector database. Flyway manages schema changes automatically on service startup, using separate migration history tables to avoid conflicts.

| Service | History table | Baseline migration |
|---|---|---|
| documentation-bot | `flyway_schema_history_bot` | V12 (applied to existing installs; fresh installs run from V1) |
| document-ingestor | `flyway_schema_history_ingestor` | V6 (same logic) |

---

## Bootstrap

The pgvector extension is bootstrapped by `init-db.sql` (run by the Docker Compose Postgres container on first start, or applied manually for bare-metal setups):

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

Both services create their schemas via Flyway on startup — no manual DDL is needed after the extension is installed.

---

## Adding a Migration

1. Create `V{N}__short_description.sql` in the service's `src/main/resources/db/migration/` directory.
2. Use the next sequential version number.
3. Never edit a migration that has already been applied to any environment — create a new migration to fix it instead.
4. Both services can modify the same tables; just ensure version numbers within each service's history table are sequential.

---

## document-ingestor Schema

### Core tables

**`documents`**

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `tenant_id` | UUID | Row-level tenant isolation |
| `name` | VARCHAR | Display name |
| `product` | VARCHAR | Product tag (used in search scope) |
| `version` | VARCHAR | Version tag |
| `status` | VARCHAR | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, `QUARANTINED` |
| `storage_key` | VARCHAR | S3 object key for the source binary |
| `embedding_model` | VARCHAR | Model used for embeddings (for cache invalidation) |
| `pii_detected` | BOOLEAN | True if PiiDetectionService flagged this document |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

**`document_chunks`**

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `document_id` | UUID FK → `documents` | |
| `tenant_id` | UUID | Denormalized for query performance |
| `chunk_index` | INTEGER | Position within the document |
| `content` | TEXT | Chunk text (Markdown) |
| `embedding` | vector(1536) | OpenAI text-embedding-3-small |
| `search_vector` | tsvector | Generated column for lexical search (GIN index) |
| `chunk_type` | VARCHAR | `PROSE`, `CODE`, `TABLE` |
| `section_heading` | VARCHAR | Nearest heading above this chunk |
| `superseded` | BOOLEAN | True when a newer version of the same document replaced this chunk |

HNSW index on `embedding`:

```sql
CREATE INDEX ON document_chunks
  USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 64);
```

GIN index on `search_vector` for lexical retrieval:

```sql
CREATE INDEX ON document_chunks USING gin(search_vector);
```

**`webhook_events`** — audit log of incoming webhook requests

**`connector_sync_pages`** — tracks which Confluence/Notion pages have been synced

**`integration_tokens`** — per-tenant integration credentials (Confluence API tokens, GitHub tokens), encrypted AES-256-GCM

### Migration history

| Migration | Description |
|---|---|
| V1 | `documents` + `document_chunks` (embedding `vector(1536)`) |
| V2 | HNSW index on `document_chunks.embedding` |
| V3 | `webhook_events` |
| V4 | `connector_sync_pages` + `integration_tokens` |
| V5 | Semantic chunking metadata (`chunk_type`, `section_heading`) |
| V6 | Tenant isolation — `tenant_id` on `documents`/`document_chunks`, S3 storage fields, PII flags |
| V7 | `integration_tokens.tenant_id` backfill |
| V8 | `storage_key` NOT NULL constraint drop (allows pre-storage records) |
| V9 | Hybrid search — `tsvector` generated column + GIN index on `document_chunks` |
| V10 | `documents.embedding_model` column |

---

## documentation-bot Schema

### Core tables

**`tenants`** — tenant records with plan, limits, branding config, LLM config, retention config

**`users`**

| Column | Notes |
|---|---|
| `id`, `tenant_id` | Row-level isolation |
| `username`, `email` | Unique within tenant |
| `password_hash` | BCrypt |
| `role` | `USER`, `ADMIN`, `SUPER_ADMIN` |
| `must_change_password` | Set true for seeded admin and invited users |
| `failed_login_attempts` | For lockout |
| `locked_until` | Account lockout expiry |
| `deactivated_at` | Soft-delete for deactivated accounts |

**`chat_sessions`** — sessions with pin, tag, title, share token

**`chat_messages`** — individual messages with role (`user` / `assistant`), content, citations (JSONB), confidence

**`chat_summaries`** — rolling LLM summaries generated at 15-message threshold

**`api_keys`** — `dak_` prefix, stored hashed, per-key expiry and rate limit

**`invitation_tokens`** — pending invitations with expiry and single-use enforcement

**`refresh_tokens`** — persisted refresh tokens for JWT refresh flow

**`password_reset_tokens`** — single-use tokens for password reset

**`document_access`** — per-user document grants

**`groups`** + **`group_memberships`** + **`group_document_access`** — group-based ACL

**`tenant_memberships`** — user ↔ tenant many-to-many (for multi-workspace)

**`answer_feedback`** — thumbs up/down + text on assistant messages

**`bookmarks`** — per-user message bookmarks with tags

**`collections`** + **`collection_items`** — curated bookmark groups

**`shared_chat_links`** — shareable chat session tokens with expiry and visibility

**`query_logs`** — per-query log including product, version, latency, token counts, cost

**`audit_log`** — immutable audit trail

**`escalations`** — low-confidence queries routed to human experts

**`topic_subscriptions`** — user product/topic subscriptions

**`notifications`** — in-app notification feed

**`faq_clusters`** — auto-generated FAQ entries from query clustering

**`gdpr_deletion_requests`** — pending erasure requests with 7-day grace period

**`tenant_llm_configs`** — per-tenant LLM provider config (API key encrypted)

**`tenant_brandings`** — per-tenant logo, colors, product name

**`tenant_retention_policies`** — per-tenant data retention configuration

**`oidc_configurations`** — per-tenant OIDC/SSO provider config

### Migration history

| Migration | Description |
|---|---|
| V1 | `users`, `chat_sessions`, `chat_messages`, `chat_summaries` |
| V2 | HNSW pgvector index |
| V3 | `answer_feedback` |
| V4 | Session pinning, `bookmarks` |
| V5 | User preferences |
| V6 | `collections`, `shared_chat_links`, answer upvotes |
| V7 | `query_logs`, cost tracking |
| V8 | `api_keys` |
| V9 | Email digest tracking |
| V10 | `faq_clusters`, version diff, query session graph |
| V11 | `tenants`, `tenant_id` on all user-data tables |
| V12 | `tenant_retention_policies`, `gdpr_deletion_requests`, `tenant_brandings`, `tenant_llm_configs` |
| V13 | `SUPER_ADMIN` role, `invitation_tokens` |
| V14 | `document_access` (per-document, per-user ACL) |
| V15 | `shared_chat_links.tenant_id` |
| V16 | Drop stale preference columns |
| V17 | `groups`, `group_memberships`, `group_document_access` |
| V18 | `tenant_memberships` join table |
| V19–V32 | `tenant_id` on audit/query/feedback tables; `gdpr_deletion_requests` refinements; `refresh_tokens`; login lockout columns; `password_reset_tokens`; user deactivation; invite revocation; OIDC config table |

---

## Useful Queries

```sql
-- List all tenants
SELECT id, name, slug, plan, max_users, max_documents FROM tenants;

-- List users in a tenant
SELECT username, email, role, created_at
FROM users
WHERE tenant_id = '<tenant-uuid>'
ORDER BY created_at;

-- List documents and their status
SELECT name, product, version, status, created_at
FROM documents
WHERE tenant_id = '<tenant-uuid>'
ORDER BY created_at DESC;

-- Count vector chunks per document
SELECT d.name, COUNT(dc.id) AS chunk_count
FROM documents d
JOIN document_chunks dc ON d.id = dc.document_id
WHERE d.tenant_id = '<tenant-uuid>'
GROUP BY d.name;

-- Find documents stuck in PROCESSING
SELECT id, name, updated_at
FROM documents
WHERE status = 'PROCESSING'
  AND updated_at < NOW() - INTERVAL '30 minutes';

-- Audit log for a user
SELECT action, target_type, target_id, metadata, created_at
FROM audit_log
WHERE actor_id = '<user-uuid>'
ORDER BY created_at DESC
LIMIT 50;
```
