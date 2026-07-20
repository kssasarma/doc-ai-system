# Security

---

## Authentication and Access Control

See [Authentication](authentication.md) for JWT, API key, and OIDC/SSO details.

See [Multi-Tenancy — Document Access Control](multi-tenancy.md#document-access-control) for per-document and per-group grants.

**Key points:**
- Invitation-only account creation prevents unauthorized tenants
- Tenant context is resolved from the authenticated principal, not from a header — an authenticated caller cannot switch to a different tenant
- `GrantBasedDocumentAccessPolicy` is the single enforcement point: every vector search query is pre-filtered to the user's granted document IDs before retrieval

---

## Rate Limiting

All rate limits are enforced by Bucket4j. When Redis is available, counters are Redis-backed (shared across multiple bot pods). Without Redis, counts are in-memory per pod.

| Endpoint | Limit | Config variable |
|---|---|---|
| Chat endpoints (per authenticated user) | 30 req/min | `RATE_LIMIT_PER_MINUTE` |
| `/api/auth/login` and `/api/auth/refresh` (per source IP) | 10 req/min | `AUTH_RATE_LIMIT_PER_MINUTE` |
| Per API key | Configurable at key creation | |

Rate-limited responses return `429 Too Many Requests` with a `Retry-After` header.

---

## SSRF Protection

`SafeUrlValidator` (document-ingestor) guards all server-side HTTP requests made during URL fetches and connector syncs.

Rules enforced before any outbound fetch:

1. **HTTPS only** — plain HTTP is blocked except for hosts in `SSRF_HTTP_ALLOWED_HOSTS` (dev/test convenience only)
2. **Private IP block** — loopback (`127.0.0.0/8`), private ranges (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), link-local, and metadata service IPs are blocked
3. **Redirect cap** — maximum 3 redirects; redirect targets are validated against the same rules
4. **Allowlist for Confluence** — Confluence site URLs must match `CONFLUENCE_HOST_ALLOWLIST` (default: `.atlassian.net`)

**Known residual gap:** DNS-rebinding attacks (TOCTOU between DNS resolution at validation time and actual connection) are not fully prevented. Mitigation: run the ingestor in a network namespace without access to internal services, and rely on the private IP block as the primary defense.

---

## Webhook Authentication

### CI/CD webhook ingestion (document-ingestor)

Incoming `POST /api/v1/ingest/webhook` requests are authenticated via HMAC-SHA-256:

```
X-Webhook-Signature: sha256=<hex-digest-of-body-using-WEBHOOK_HMAC_SECRET>
```

Computing the signature in a CI script:

```bash
BODY='{"url":"https://docs.example.com/guide.html","product":"MyProduct","version":"2.1.0"}'
SIG="sha256=$(echo -n "$BODY" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" | awk '{print $2}')"
```

Without `WEBHOOK_HMAC_SECRET` set, the endpoint falls back to requiring an ADMIN JWT.

### Bot → ingestor internal API

Internal calls from documentation-bot to document-ingestor (e.g. presigned URL generation for citation downloads) are authenticated via `InternalServiceAuthFilter`. The `Authorization` header carries a timestamp + HMAC-SHA-256 of the timestamp using `INTERNAL_SERVICE_SECRET`. Without this secret, the internal API is disabled entirely.

---

## Secrets Encryption (BYO LLM Keys)

Per-tenant LLM API keys and connector integration tokens entered via the admin panel are encrypted at rest using AES-256-GCM:

- `SecretsCryptoService` generates a random 12-byte IV for each encryption operation
- The IV is prepended to the ciphertext before storage
- `SECRETS_ENCRYPTION_KEY` (32-byte base64) is the key material — must be the same on both services and must be stored securely (not in git)

**Key rotation:** Not currently automated. Rotating the key requires decrypting all stored secrets with the old key and re-encrypting with the new one. Store the key in a secret manager (see [Deployment — Secrets Management](deployment.md#secrets-management)).

---

## GDPR Compliance

### User data export (Art. 20)

A user can download all their personal data as a JSON archive:

```bash
curl http://localhost:8082/api/user/gdpr/export \
  -H "Authorization: Bearer <jwt>" \
  --output my-data.json
```

The archive includes: profile info, all chat sessions and messages, bookmarks, collections, feedback, subscriptions, API key names (not secrets), query logs.

### Account erasure (Art. 17)

```bash
curl -X DELETE http://localhost:8082/api/user/gdpr/me \
  -H "Authorization: Bearer <jwt>"
```

This creates a deletion request with a 7-day grace period (allows recovery if accidentally triggered). After 7 days, `DataRetentionService` (runs nightly at 03:00 UTC) processes it:

- Cascades through all user-owned data (sessions, messages, bookmarks, etc.)
- Anonymises audit log entries (replaces user-identifying fields with `[DELETED]`)
- Revokes active JWT refresh tokens
- Removes the user account

Admins can view pending deletion requests at `GET /api/admin/gdpr/deletion-requests` and process them immediately if needed.

### Data retention policies

Per-tenant retention is configured via `PUT /api/admin/tenants/{id}/retention`. The nightly `DataRetentionService` enforces these limits by deleting records older than the configured period.

---

## PII Detection

document-ingestor scans every document for PII before chunking and embedding. See [Document Ingestion — PII Handling](document-ingestion.md#pii-handling) for the full list of patterns detected and the quarantine / approval workflow.

---

## Audit Log

Every significant action is written to an immutable `audit_log` table:

| Field | Type | Description |
|---|---|---|
| `actor_id` | UUID | User who performed the action |
| `action` | VARCHAR | e.g. `QUERY`, `UPLOAD`, `DELETE`, `LOGIN`, `EXPORT`, `ROLE_CHANGE` |
| `target_type` | VARCHAR | e.g. `DOCUMENT`, `CHAT`, `USER`, `COLLECTION` |
| `target_id` | UUID | ID of the affected resource |
| `metadata` | JSONB | Action-specific detail (query text, document name, etc.) |
| `ip_address` | VARCHAR | Source IP |
| `created_at` | TIMESTAMP | UTC |

Admins can filter and search the log via `GET /api/admin/audit-logs`. Records are never modified or deleted (GDPR erasure anonymises, not deletes, audit rows).

---

## Login Security

| Feature | Default |
|---|---|
| Failed login lockout threshold | 10 attempts (`AUTH_MAX_FAILED_ATTEMPTS`) |
| Lockout duration | 15 minutes (`AUTH_LOCKOUT_DURATION_MINUTES`) |
| Lockout response | `423 Locked` with `Retry-After` header |
| Password reset | Token-based via email; single-use |
| JWT refresh token revocation | Logout invalidates the refresh token; access tokens are short-lived (24 h) |

---

## CORS

CORS is controlled by the `CORS_ALLOWED_ORIGINS` variable on both services. In production, list only the exact origins you control:

```dotenv
CORS_ALLOWED_ORIGINS=https://app.your-domain.com,chrome-extension://<extension-id>
```

Never set `*` in production. The dev default (`localhost:5173,localhost:3000`) is replaced by `prod` profile startup validation.

---

## Network Policies (Kubernetes)

The Kubernetes manifests include a default-deny `NetworkPolicy` with explicit allows:

| Source | Destination | Port | Purpose |
|---|---|---|---|
| Ingress controller | documentation-bot | 8082 | User-facing API |
| Ingress controller | document-ingestor | 8081 | Upload and webhook endpoints |
| documentation-bot | document-ingestor | 8081 | Internal API (citation presigned URLs) |
| documentation-bot | PostgreSQL | 5432 | JPA |
| documentation-bot | Redis | 6379 | Rate limiting + embedding cache |
| document-ingestor | PostgreSQL | 5432 | JPA |
| document-ingestor | AWS S3 / MinIO | 443 / 9000 | Binary storage |

All other pod-to-pod traffic is denied.
