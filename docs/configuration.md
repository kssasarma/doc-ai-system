# Configuration

All configuration is environment-variable-driven. Reasonable defaults exist for local development. Production deployments must set the variables marked **required in production**.

Copy `.env.example` to `.env` and fill in your values. Docker Compose reads `.env` automatically.

---

## Shared (both services must use the same values)

| Variable | Required in prod | Description |
|---|---|---|
| `SPRING_DATABASE_URL` | Yes | PostgreSQL JDBC URL. Default: `jdbc:postgresql://localhost:5432/docai` |
| `SPRING_DATABASE_USERNAME` | Yes | Database username. Default: `postgres` |
| `SPRING_DATABASE_PASSWORD` | Yes | Database password. Default: `postgres` |
| `JWT_SECRET` | Yes | Base64-encoded secret for signing JWTs. Must decode to ≥32 bytes. **Generate:** `openssl rand -base64 64` |
| `SECRETS_ENCRYPTION_KEY` | Yes | Base64-encoded AES-256 key for encrypting per-tenant LLM keys and integration tokens at rest. Must decode to exactly 32 bytes. **Generate:** `openssl rand -base64 32`. **Both services must have the same value.** |
| `INTERNAL_SERVICE_SECRET` | Yes | HMAC secret for bot→ingestor internal API calls. Unset disables the internal API. |
| `OPENAI_API_KEY` | Yes | OpenAI API key — used for embeddings by the ingestor and for chat completions by the bot. |

---

## documentation-bot

### Core

| Variable | Default | Description |
|---|---|---|
| `OPENAI_CHAT_MODEL` | `gpt-4o-mini` | Default chat model when no tenant-level config overrides it |
| `OPENAI_EMBEDDING_MODEL` | `text-embedding-3-small` | Must match document-ingestor's embedding model |
| `ANTHROPIC_API_KEY` | *(disabled)* | Enables Anthropic Claude; without this key Anthropic provider is unavailable and tenants configured to use it fall back to OpenAI |
| `ANTHROPIC_CHAT_MODEL` | `claude-sonnet-4-6` | Anthropic model to use when `ANTHROPIC_API_KEY` is set |

### Authentication

| Variable | Default | Description |
|---|---|---|
| `JWT_EXPIRATION_MS` | `86400000` | Access token lifetime in milliseconds (default: 24 hours) |
| `JWT_REFRESH_EXPIRATION_MS` | `2592000000` | Refresh token lifetime (default: 30 days) |
| `AUTH_RATE_LIMIT_PER_MINUTE` | `10` | Max unauthenticated requests per minute per IP to `/api/auth/login` and `/api/auth/refresh` |
| `AUTH_MAX_FAILED_ATTEMPTS` | `10` | Failed login attempts before an account is locked out |
| `AUTH_LOCKOUT_DURATION_MINUTES` | `15` | How long the lockout lasts |

### Seeded admin (first-run bootstrap)

| Variable | Default | Description |
|---|---|---|
| `SEED_ADMIN_USERNAME` | `admin` | SUPER_ADMIN username, created only when the `users` table is empty |
| `SEED_ADMIN_EMAIL` | `admin@docs-inator.local` | SUPER_ADMIN email |
| `SEED_ADMIN_PASSWORD` | `Opentext123$` | SUPER_ADMIN initial password. **Always override.** The account is forced to change this on first login regardless. |

### Rate limiting

| Variable | Default | Description |
|---|---|---|
| `RATE_LIMIT_PER_MINUTE` | `30` | Max chat requests per minute per authenticated user |

### CORS

| Variable | Default | Description |
|---|---|---|
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Comma-separated list. Must include the browser extension origin if you use the extension: `chrome-extension://<extension-id>` |

### Redis (optional)

Omit `REDIS_HOST` entirely to disable the embedding cache. The bot falls back to calling OpenAI for every embedding request.

| Variable | Default | Description |
|---|---|---|
| `REDIS_HOST` | *(disabled)* | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | *(none)* | Redis AUTH password |

### Email

| Variable | Default | Description |
|---|---|---|
| `MAIL_HOST` | `smtp.gmail.com` | SMTP hostname |
| `MAIL_PORT` | `587` | SMTP port |
| `MAIL_USERNAME` | *(disabled)* | SMTP username. Invitations and digests are disabled without this. |
| `MAIL_PASSWORD` | *(disabled)* | SMTP password |
| `MAIL_FROM` | `noreply@docs-inator.example.com` | From-address in outgoing emails |
| `MAIL_FROM_NAME` | `Docs-inator` | From-name in outgoing emails |
| `APP_URL` | `http://localhost:5173` | Base URL used to build links in invitation emails. **Must be the public URL of the frontend in production.** |

### RAG tuning

| Variable | Default | Description |
|---|---|---|
| `BOT_MIN_SIMILARITY_THRESHOLD` | `0.55` | Chunks with cosine similarity below this are excluded from context. Below-threshold queries return an honest "not found" rather than a hallucinated answer. |
| `BOT_COST_INPUT_PER_1K` | `0.00015` | USD cost per 1K input tokens, used in cost-tracking analytics |
| `BOT_COST_OUTPUT_PER_1K` | `0.00060` | USD cost per 1K output tokens, used in cost-tracking analytics |

### Ingestor integration

| Variable | Default | Description |
|---|---|---|
| `INGESTOR_INTERNAL_URL` | `http://document-ingestor:8081` | document-ingestor base URL for resolving presigned citation download URLs |

---

## document-ingestor

### Storage

| Variable | Default | Description |
|---|---|---|
| `S3_BUCKET` | `docai-documents` | S3/MinIO bucket name |
| `S3_ENDPOINT` | `http://minio:9000` | S3 endpoint. **Leave empty** (`S3_ENDPOINT=`) for real AWS S3. Set to `http://localhost:9000` for bare-metal MinIO. |
| `S3_REGION` | `us-east-1` | S3 region |
| `S3_ACCESS_KEY` | `minioadmin` | S3 access key |
| `S3_SECRET_KEY` | `minioadmin123` | S3 secret key |
| `S3_PATH_STYLE_ACCESS` | `true` | Required for MinIO. Set `false` for real AWS S3 (virtual-hosted-style). |
| `MAX_UPLOAD_SIZE` | `100MB` | Maximum single-file upload size |
| `MAX_REQUEST_SIZE` | `100MB` | Maximum multipart request size |

### Ingestion behaviour

| Variable | Default | Description |
|---|---|---|
| `STUCK_PROCESSING_TIMEOUT_MINUTES` | `30` | Documents stuck in `PROCESSING` longer than this are reaped to `FAILED` by the nightly scheduler |

### Security

| Variable | Default | Description |
|---|---|---|
| `WEBHOOK_HMAC_SECRET` | *(disabled)* | Shared secret CI/CD systems use to sign `POST /api/v1/ingest/webhook` request bodies with HMAC-SHA-256. Without this, webhook ingestion falls back to requiring an ADMIN JWT. |
| `SSRF_HTTP_ALLOWED_HOSTS` | `localhost,127.0.0.1` | Hosts allowed over plain HTTP for server-side URL fetches. **Dev/test only — never add real external hosts here.** |
| `CONFLUENCE_HOST_ALLOWLIST` | `.atlassian.net` | Required hostname suffix for registered Confluence site URLs |

### CORS

| Variable | Default | Description |
|---|---|---|
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Comma-separated allowed origins |

---

## Frontend (Vite build-time variables)

These are baked into the frontend at build time by Vite. If you change the server addresses after building, you must rebuild the frontend image.

| Variable | Default | Description |
|---|---|---|
| `VITE_BACKEND_URL` | `http://localhost:8082` | documentation-bot base URL as seen from the browser |
| `VITE_INGESTOR_URL` | `http://localhost:8081` | document-ingestor base URL as seen from the browser |

---

## Generating Required Secrets

```bash
# JWT_SECRET (64 random bytes, base64-encoded)
openssl rand -base64 64

# SECRETS_ENCRYPTION_KEY (exactly 32 bytes decoded → 44 chars base64)
openssl rand -base64 32

# INTERNAL_SERVICE_SECRET (any strong random string)
openssl rand -hex 32
```

---

## Production Checklist

Before any real deployment, verify:

- [ ] `JWT_SECRET` is a freshly generated random value, not the dev default
- [ ] `SECRETS_ENCRYPTION_KEY` is the same value on both services and stored securely
- [ ] `SEED_ADMIN_PASSWORD` is set to a strong value (you will be forced to change it on first login anyway)
- [ ] `S3_ENDPOINT` is empty (real AWS S3) or points at your MinIO instance
- [ ] `CORS_ALLOWED_ORIGINS` lists only your actual frontend origins
- [ ] `APP_URL` points to the public URL of the frontend (for invitation email links)
- [ ] `INTERNAL_SERVICE_SECRET` is set (bot→ingestor internal API is disabled without it)
- [ ] `MAIL_USERNAME` / `MAIL_PASSWORD` are set if you want email delivery

For Kubernetes deployments, see [Deployment — Secrets Management](deployment.md#secrets-management).
