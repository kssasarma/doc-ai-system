# Docs-inator — AI Documentation Intelligence Platform

> Every developer, support engineer, and technical writer using versioned product documentation should reach for Docs-inator the same way they reach for Google — except the answers are always grounded in *your* documentation, *your* version, and *your* team's institutional knowledge.

---

## Table of Contents

1. [Architecture](#architecture)
2. [Feature Matrix](#feature-matrix)
3. [Prerequisites](#prerequisites)
4. [Quick Start (Local)](#quick-start-local)
5. [Services](#services)
6. [Configuration Reference](#configuration-reference)
7. [Authentication](#authentication)
8. [Multi-Tenancy](#multi-tenancy)
9. [LLM Routing](#llm-routing)
10. [Document Ingestion](#document-ingestion)
11. [Integrations](#integrations)
12. [API Reference](#api-reference)
13. [Database Migrations](#database-migrations)
14. [Deployment](#deployment)
15. [Monitoring](#monitoring)
16. [Project Structure](#project-structure)

---

## Architecture

### System Diagram

```mermaid
graph TB
    subgraph Clients["Client Layer"]
        FE["React Frontend\n(Vite · port 5173)"]
        SLK["Slack Bot\n(Node.js)"]
        TMS["Teams Bot\n(Node.js)"]
        VSC["VS Code Extension\n(TypeScript)"]
        BRW["Browser Extension\n(Chrome)"]
    end

    subgraph Gateway["Ingress / API Gateway"]
        NGX["nginx Ingress\n(TLS termination)"]
    end

    subgraph BotService["documentation-bot  :8082"]
        direction TB
        CHAT["ChatController"]
        AUTH["AuthController\n+ OIDC"]
        APIV1["Public API v1\n(API-Key auth)"]
        ADMIN["Admin Controllers\n(ADMIN role)"]

        subgraph BotCore["Core Services"]
            CS["ChatService\n+ ContextManager"]
            AGS["AnswerGenerationService"]
            VSS["VectorSearchService"]
            LLR["LLMRouter\n(OpenAI · Anthropic)"]
            ECS["EmbeddingCacheService\n(Redis · optional)"]
        end

        subgraph Intelligence["Intelligence Layer"]
            MHS["MultiHopReasoningService"]
            PAA["PeopleAlsoAskedService"]
            AFS["AutoFaqService"]
            VDS["VersionDiffService"]
            DGS["DocumentationGapService"]
        end

        subgraph Enterprise["Enterprise Layer"]
            TNS["TenantService\n+ TenantResolutionFilter"]
            OIDCS["OidcJitProvisioningService"]
            DRS["DataRetentionService\n(nightly @3am)"]
            DES["DataExportService\n(GDPR Art. 20)"]
        end
    end

    subgraph IngestorService["document-ingestor  :8081"]
        direction TB
        ING["IngestionController\n/api/ingest"]
        WBH["WebhookController"]
        CNT["ConnectorController"]

        subgraph Pipeline["Ingestion Pipeline"]
            PRS["Parsers\n(PDF · CHM · HTML · TXT)"]
            CHK["Chunkers\n(Fixed-size · Semantic)"]
            PII["PiiDetectionService\n(SSN · CC · AWS_KEY)"]
            EMB["EmbeddingService\n(OpenAI)"]
        end

        subgraph StorageBackend["Storage Backends"]
            LOC["Local Filesystem\n(default)"]
            S3S["AWS S3\n(optional)"]
        end

        subgraph Connectors["Source Connectors"]
            CONF["Confluence Cloud"]
            NOTI["Notion"]
            DW["Directory Watcher"]
        end
    end

    subgraph DataLayer["Data Layer"]
        PG[("PostgreSQL 16\n+ pgvector\nHNSW index")]
        RD[("Redis 7\nembedding cache\n1-hour TTL")]
    end

    subgraph ExternalAI["LLM Providers"]
        OAI["OpenAI\n(GPT-4o · text-embedding)"]
        ANT["Anthropic\n(Claude Sonnet)"]
    end

    FE & SLK & TMS & VSC & BRW -->|HTTPS| NGX
    NGX -->|/api/ingest\n/api/documents| IngestorService
    NGX -->|all other /api| BotService

    CHAT --> CS --> AGS --> LLR
    AGS --> VSS --> ECS
    ECS -->|miss| OAI
    LLR --> OAI
    LLR -.->|optional| ANT

    ING --> Pipeline --> StorageBackend
    Pipeline --> PG
    CONF --> PRS
    NOTI --> PRS

    BotService <-->|JPA| PG
    IngestorService <-->|JPA| PG
    ECS <-->|GET/SET| RD
```

### Chat Query Flow

```text
User Message
    │
    ▼
TenantResolutionFilter  →  resolves X-Tenant-Id / JWT claim / default tenant
    │
    ▼
RateLimitFilter         →  30 req/min per user (Bucket4j)
    │
    ▼
JwtAuthFilter / ApiKeyAuthFilter
    │
    ▼
ChatController.sendMessage()
    ├── ContextManager        sliding window (last 10 msgs + LLM summary)
    ├── VectorSearchService   cosine similarity, product+version filtered, top-7
    │       └── EmbeddingCacheService  →  Redis (SHA-256 key) or OpenAI
    ├── MultiHopReasoningService       (for complex multi-step queries)
    ├── AnswerGenerationService
    │       └── LLMRouter  →  tenant config  →  OpenAI / Anthropic + fallback
    ├── QueryLog saved (async)
    ├── PeopleAlsoAskedService (async)
    └── Response + citations + follow-up questions
```

---

## Feature Matrix

| Feature | Phase | Status |
| --- | --- | --- |
| RAG pipeline (PDF / CHM / HTML / TXT) | 0 | ✅ |
| Flyway versioned schema migrations | 0 | ✅ |
| HNSW pgvector index | 0 | ✅ |
| Circuit breakers + retry (Resilience4j) | 0 | ✅ |
| Rate limiting (Bucket4j) | 0 | ✅ |
| JWT authentication + ADMIN/USER roles | 0 | ✅ |
| Answer feedback (thumbs up/down) | 1 | ✅ |
| Source citations with chunk references | 1 | ✅ |
| Answer confidence scoring | 1 | ✅ |
| Chunk annotations | 1 | ✅ |
| Chat sessions with persistent history | 2 | ✅ |
| Auto-summarization at 15 messages | 2 | ✅ |
| Bookmarks | 2 | ✅ |
| User preferences | 2 | ✅ |
| Collections (bookmark groups) | 3 | ✅ |
| Shared chat links | 3 | ✅ |
| Answer upvoting | 3 | ✅ |
| Query analytics dashboard | 4 | ✅ |
| Documentation gap reports | 4 | ✅ |
| Escalation workflow | 4 | ✅ |
| Cost tracking (token usage) | 4 | ✅ |
| Full audit log | 4 | ✅ |
| Product / version access control | 4 | ✅ |
| Public REST API v1 (API key auth) | 5 | ✅ |
| Webhook ingestion (CI/CD) | 5 | ✅ |
| Confluence Cloud connector | 5 | ✅ |
| Notion connector | 5 | ✅ |
| Email digest | 5 | ✅ |
| Slack bot | 5 | ✅ |
| Microsoft Teams bot | 5 | ✅ |
| Browser extension (Chrome) | 5 | ✅ |
| VS Code extension | 5 | ✅ |
| Semantic chunking | 6 | ✅ |
| Multi-hop reasoning | 6 | ✅ |
| People Also Asked | 6 | ✅ |
| Version diff ("what changed?") | 6 | ✅ |
| Answer evolution tracking | 6 | ✅ |
| Auto-generated FAQ clusters | 6 | ✅ |
| Topic subscriptions | 6 | ✅ |
| Multi-tenancy (row-level isolation) | 7 | ✅ |
| OIDC / SSO JIT provisioning | 7 | ✅ |
| GDPR data export (Art. 20) + erasure (Art. 17) | 7 | ✅ |
| Data retention policies (per tenant) | 7 | ✅ |
| Multi-LLM routing (OpenAI + Anthropic) | 7 | ✅ |
| Redis embedding cache | 7 | ✅ |
| AWS S3 document storage | 7 | ✅ |
| PII detection and flagging | 7 | ✅ |
| White-label branding (per tenant) | 7 | ✅ |
| Kubernetes manifests + HPAs | 7 | ✅ |
| Helm chart | 7 | ✅ |

---

## Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| Java | 21 | Spring Boot 4.0.2 requires Java 21 |
| Maven | 3.9+ | Wrapper included (`./mvnw`) |
| Node.js | 20+ | Frontend and bot integrations |
| PostgreSQL | 16 | Must have `pgvector` extension |
| Docker | 24+ | For containerised local development |
| OpenAI API key | — | Required (embeddings + chat) |

**Optional (enable extra features):**

| Tool | Enables |
| --- | --- |
| Anthropic API key | Claude as alternate LLM |
| Redis | Embedding cache (degrades gracefully without it) |
| AWS credentials | S3 document storage (local filesystem used otherwise) |
| SMTP credentials | Email digest feature |

---

## Quick Start (Local)

### 1. Start PostgreSQL with pgvector

```bash
docker run -d \
  --name docai-postgres \
  -e POSTGRES_DB=docai \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  pgvector/pgvector:pg16
```

### 2. Start document-ingestor

```bash
cd document-ingestor
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
# Listening on http://localhost:8081
```

### 3. Start documentation-bot

```bash
cd documentation-bot
export OPENAI_API_KEY=sk-...
export JWT_SECRET=$(openssl rand -base64 64)
./mvnw spring-boot:run
# Listening on http://localhost:8082
```

### 4. Start the frontend

```bash
cd frontend
npm install
npm run dev
# Opens http://localhost:5173/docs-inator
```

### 5. Create the first admin account

```bash
curl -s -X POST http://localhost:8082/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","email":"admin@example.com","password":"changeme","role":"ADMIN"}'
```

### 6. Upload a document

```bash
TOKEN=$(curl -s -X POST http://localhost:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"changeme"}' | jq -r .token)

curl -X POST http://localhost:8081/api/ingest \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@my-manual.pdf" \
  -F "product=MyProduct" \
  -F "version=2.1.0"
```

---

## Services

### documentation-bot (port 8082)

The main API service. Handles all user interactions, authentication, chat, and AI reasoning.

**Key responsibilities:**
- Multi-turn chat sessions with sliding-window context management
- Product + version scoped vector similarity search
- Multi-LLM routing with automatic fallback
- JWT + API key authentication, rate limiting, OIDC JIT provisioning
- Multi-tenancy via `TenantResolutionFilter` (ThreadLocal context)
- GDPR compliance (data export and erasure)
- Auto-generated FAQ, version diff, multi-hop reasoning, People Also Asked
- Admin analytics, gap reports, escalation workflow, cost tracking
- Email digests, in-app notifications, topic subscriptions

### document-ingestor (port 8081)

The ingestion pipeline service. Processes raw documents into searchable vector embeddings.

**Key responsibilities:**
- Document parsing: PDF, CHM, HTML, plain text
- Chunking strategies: fixed-size (512 tokens, 50-token overlap) and embedding-based semantic
- OpenAI embedding generation and storage into pgvector
- PII detection and flagging before storage (SSN, credit cards, AWS keys, email, phone, IP)
- Storage backends: local filesystem (default) or AWS S3
- Source connectors: Confluence Cloud, Notion, directory watcher
- Webhook ingestion for CI/CD pipeline integration

---

## Configuration Reference

All configuration is environment-variable-driven. Defaults work out of the box for local development.

### documentation-bot

| Variable | Default | Description |
| --- | --- | --- |
| `OPENAI_API_KEY` | **required** | OpenAI API key |
| `JWT_SECRET` | weak dev value | Base64-encoded secret — **always override in production** |
| `SPRING_DATABASE_URL` | `jdbc:postgresql://localhost:5432/docai` | PostgreSQL JDBC URL |
| `SPRING_DATABASE_USERNAME` | `postgres` | DB username |
| `SPRING_DATABASE_PASSWORD` | `postgres` | DB password |
| `ANTHROPIC_API_KEY` | *(disabled)* | Enables Anthropic Claude as LLM option |
| `REDIS_HOST` | *(disabled)* | Redis hostname — omit to disable embedding cache |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | *(none)* | Redis auth password |
| `OPENAI_CHAT_MODEL` | `gpt-4o-mini` | Default chat model |
| `OPENAI_EMBEDDING_MODEL` | `gpt-4o-embedding-4k` | Embedding model |
| `ANTHROPIC_CHAT_MODEL` | `claude-sonnet-4-6` | Anthropic model (when `ANTHROPIC_API_KEY` is set) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,...` | Comma-separated allowed CORS origins |
| `RATE_LIMIT_PER_MINUTE` | `30` | Max requests per minute per user |
| `JWT_EXPIRATION_MS` | `86400000` | Token lifetime (default: 24 hours) |
| `MAIL_HOST` | `smtp.gmail.com` | SMTP host for email digests |
| `MAIL_USERNAME` | *(disabled)* | SMTP username |
| `MAIL_PASSWORD` | *(disabled)* | SMTP password |
| `AWS_REGION` | `us-east-1` | AWS region |
| `AWS_ACCESS_KEY_ID` | *(disabled)* | AWS access key |
| `AWS_SECRET_ACCESS_KEY` | *(disabled)* | AWS secret key |
| `DEFAULT_TENANT_ID` | `00000000-0000-0000-0000-000000000001` | Default tenant UUID |

### document-ingestor

| Variable | Default | Description |
| --- | --- | --- |
| `OPENAI_API_KEY` | **required** | OpenAI API key (for embeddings) |
| `SPRING_DATABASE_URL` | `jdbc:postgresql://localhost:5432/docai` | Same database as bot |
| `STORAGE_TYPE` | `local` | `local` or `s3` |
| `S3_BUCKET` | `docai-documents` | S3 bucket name (when `STORAGE_TYPE=s3`) |
| `AWS_REGION` | `us-east-1` | AWS region |
| `AWS_ACCESS_KEY_ID` | *(disabled)* | AWS access key |
| `AWS_SECRET_ACCESS_KEY` | *(disabled)* | AWS secret key |

---

## Authentication

Three methods are supported; they are evaluated in order per request.

### 1. JWT (interactive users)

```bash
# Register
curl -X POST http://localhost:8082/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","password":"secret","role":"USER"}'

# Login → returns {"token":"eyJ..."}
curl -X POST http://localhost:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"secret"}'

# Subsequent requests
Authorization: Bearer eyJ...
```

### 2. API Keys (integrations and CI/CD)

```bash
# Create a key (requires JWT auth)
curl -X POST http://localhost:8082/api/user/api-keys \
  -H "Authorization: Bearer eyJ..." \
  -H "Content-Type: application/json" \
  -d '{"name":"CI pipeline"}'
# Returns {"key":"dak_..."}

# Use the key
X-API-Key: dak_...
```

### 3. OIDC / SSO (enterprise tenants)

The backend performs JIT provisioning — your frontend authenticates with the IdP (Google, Okta, Azure AD, etc.), then sends the decoded claims to the backend which creates or updates the local user and issues an app JWT.

```bash
# 1. Get tenant OIDC configuration
GET /api/auth/oidc/config?slug=acme

# 2. Exchange IdP claims for an app JWT
POST /api/auth/oidc/callback
Content-Type: application/json
{
  "provider": "google",
  "claims": {
    "sub": "1234567890",
    "email": "alice@acme.com",
    "name": "Alice Smith",
    "picture": "https://..."
  }
}
# Returns {"token":"eyJ..."}
```

OIDC is configured per-tenant via **Admin → Tenants → Edit**.

---

## Multi-Tenancy

All data tables carry a `tenant_id` column. Queries are automatically scoped to the current tenant — no data from one tenant is ever visible to another.

**Tenant resolution order** (per request):

| Priority | Source | Header / Claim |
| --- | --- | --- |
| 1 | Request header (UUID) | `X-Tenant-Id` |
| 2 | Request header (slug lookup) | `X-Tenant-Slug` |
| 3 | JWT claim | `tenantId` |
| 4 | Default tenant | `00000000-0000-0000-0000-000000000001` |

The resolved UUID is stored in `TenantContext` (ThreadLocal) for the request lifetime and always cleared in a `finally` block to prevent leaks between requests.

**Creating a tenant** (ADMIN role required):

```bash
POST /api/admin/tenants
Authorization: Bearer <admin-token>
Content-Type: application/json
{
  "name": "Acme Corp",
  "slug": "acme",
  "plan": "ENTERPRISE",
  "maxUsers": 500,
  "maxDocuments": 10000
}
```

---

## LLM Routing

`LLMRouter` selects the provider and model for each request based on the calling tenant's configuration, with automatic fallback to OpenAI if the primary provider fails.

```text
Tenant has LLM config?
  ├─ YES, smartRouting = true
  │     simple query  →  simpleQueryModel  (e.g. gpt-4o-mini)
  │     complex query →  complexQueryModel (e.g. gpt-4o)
  ├─ YES, smartRouting = false
  │     always use  →  chatModel
  └─ NO  →  OpenAI gpt-4o-mini (platform default)

Any provider exception  →  automatic fallback to OpenAI gpt-4o-mini
```

**Configure per tenant** (ADMIN role):

```bash
PUT /api/admin/tenants/{id}/llm-config
Content-Type: application/json
{
  "provider": "anthropic",
  "chatModel": "claude-sonnet-4-6",
  "embeddingProvider": "openai",
  "smartRouting": true,
  "simpleQueryModel": "gpt-4o-mini",
  "complexQueryModel": "gpt-4o"
}
```

Available providers: `openai`, `anthropic`, `azure_openai`

Anthropic is only active when `ANTHROPIC_API_KEY` is set. If a tenant is configured to use Anthropic but the key is missing, routing falls back to OpenAI automatically.

---

## Document Ingestion

### Upload a document

```bash
curl -X POST http://localhost:8081/api/ingest \
  -H "Authorization: Bearer <token>" \
  -F "file=@installation-guide.pdf" \
  -F "product=MyProduct" \
  -F "version=2.1.0"
```

Supported formats: `.pdf`, `.chm`, `.html`, `.htm`, `.txt`, `.md`

The pipeline runs: parse → chunk → PII scan → embed → store.

### Chunking strategies

| Strategy | When to use | Parameters |
| --- | --- | --- |
| Fixed-size | Uniform structured docs (API references) | 512 tokens, 50-token overlap |
| Semantic | Narrative docs (user guides, tutorials) | Embedding-based boundary detection |

Semantic chunking detects natural topic boundaries by comparing embedding similarity between consecutive sentences — a split is inserted where similarity drops below threshold.

### Webhook ingestion (CI/CD)

Trigger re-ingestion automatically when documentation is published:

```bash
# Register a webhook
POST /api/webhooks
{"product": "MyProduct", "version": "2.1.0", "secret": "webhook-secret"}

# Fire on doc publish
POST /api/webhooks/{webhookId}/ingest
X-Webhook-Signature: sha256=<hmac-sha256 of body>
Content-Type: application/json
{"url": "https://docs.example.com/page", "content": "..."}
```

### Connector sync

```bash
# Confluence — sync a space
POST /api/connectors/confluence/sync
{"spaceKey": "ENG", "product": "MyProduct", "version": "latest"}

# Notion — sync a database
POST /api/connectors/notion/sync
{"databaseId": "abc123", "product": "MyProduct", "version": "latest"}
```

Integration tokens are stored per-tenant in the `integration_tokens` table.

---

## Integrations

### Slack Bot

```bash
cd slack-bot && npm install
SLACK_BOT_TOKEN=xoxb-... \
SLACK_APP_TOKEN=xapp-... \
DOCS_AI_URL=http://localhost:8082 \
DOCS_AI_TOKEN=<api-key> \
node src/index.js
```

Usage: `@docs-inator What changed between v2.0 and v2.1?`

### Microsoft Teams Bot

```bash
cd teams-bot && npm install
MicrosoftAppId=<app-id> \
MicrosoftAppPassword=<app-password> \
DOCS_AI_URL=http://localhost:8082 \
DOCS_AI_TOKEN=<api-key> \
node src/index.js
```

### VS Code Extension

1. `cd vscode-extension && npm install`
2. Open in VS Code and press **F5** to launch Extension Development Host
3. Use the command palette: `Docs-inator: Ask`

### Browser Extension (Chrome)

1. Open `chrome://extensions/`
2. Enable **Developer mode**
3. Click **Load unpacked** → select `browser-extension/`
4. Highlight text on any page → click the Docs-inator icon → ask your question

---

## API Reference

Full interactive spec is served at runtime:
- `GET /v3/api-docs` — OpenAPI JSON
- `GET /swagger-ui.html` — Swagger UI

### Chat

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| `POST` | `/api/chat/sessions` | JWT | Create session |
| `GET` | `/api/chat/sessions` | JWT | List your sessions |
| `POST` | `/api/chat/sessions/{id}/messages` | JWT | Send a message |
| `GET` | `/api/chat/sessions/{id}/messages` | JWT | Get message history |
| `DELETE` | `/api/chat/sessions/{id}` | JWT | Delete session |
| `POST` | `/api/chat/sessions/{id}/pin` | JWT | Pin / unpin |
| `POST` | `/api/chat/sessions/{id}/rename` | JWT | Rename |
| `GET` | `/api/share/{token}` | Public | View shared chat |

### Public API v1

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| `POST` | `/api/v1/query` | API-Key | Query documentation |
| `GET` | `/api/v1/products` | API-Key | List products |
| `GET` | `/api/v1/products/{id}/versions` | API-Key | List versions |

### Intelligence

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| `POST` | `/api/intelligence/multi-hop` | JWT | Multi-step reasoning |
| `GET` | `/api/intelligence/people-also-asked/{sessionId}` | JWT | Related questions |
| `GET` | `/api/intelligence/version-diff` | JWT | What changed between versions |
| `GET` | `/api/intelligence/answer-evolution/{topic}` | JWT | How answers evolved |
| `GET` | `/api/faq` | Public | Browse FAQ clusters |

### Bookmarks & Collections

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/user/bookmarks` | Bookmark a message |
| `GET` | `/api/user/bookmarks` | List bookmarks |
| `POST` | `/api/user/collections` | Create collection |
| `POST` | `/api/user/collections/{id}/items` | Add to collection |

### Subscriptions & Notifications

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/user/subscriptions` | Subscribe to a topic |
| `GET` | `/api/user/subscriptions` | List subscriptions |
| `GET` | `/api/user/notifications` | Get notifications |

### Admin (ADMIN role)

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/admin/analytics/overview` | Query volume, top queries |
| `GET` | `/api/admin/gap-reports` | Documentation gaps |
| `GET` | `/api/admin/escalations` | Escalated queries |
| `GET` | `/api/admin/audit-logs` | Full audit trail |
| `GET/POST` | `/api/admin/tenants` | List / create tenants |
| `GET/PUT` | `/api/admin/tenants/{id}/branding` | White-label branding |
| `GET/PUT` | `/api/admin/tenants/{id}/llm-config` | LLM routing config |
| `GET/PUT` | `/api/admin/tenants/{id}/retention` | Data retention policy |
| `GET` | `/api/admin/users/{id}/access` | User product access |

### GDPR

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/user/gdpr/export` | Download all your data (JSON file) |
| `DELETE` | `/api/user/gdpr/me` | Request account erasure |
| `GET` | `/api/user/gdpr/admin/deletion-requests` | Admin: pending erasure requests |

---

## Database Migrations

Flyway manages all schema changes automatically on startup. Each service has its own migration history table (`flyway_schema_history_bot` and `flyway_schema_history_ingestor`) so both can share the same database without conflicts.

To add a migration: create `V{N}__short_description.sql` in `src/main/resources/db/migration/`. Never edit an already-applied migration.

### documentation-bot (V1 – V12)

| Version | Description |
| --- | --- |
| V1 | Initial schema — users, documents, chunks, chat, feedback |
| V2 | HNSW pgvector index on `document_chunks.embedding` |
| V3 | Answer feedback table |
| V4 | Chat session pinning, bookmarks |
| V5 | User preferences |
| V6 | Collections, shared chat links, answer upvotes |
| V7 | Analytics — query logs, cost tracking |
| V8 | API keys |
| V9 | Email digest tracking |
| V10 | Intelligence — FAQ clusters, version diff, query session graph |
| V11 | Multi-tenancy — `tenants` table, `tenant_id` on all user-data tables |
| V12 | Compliance — data retention, GDPR deletion requests, tenant branding, tenant LLM configs |

### document-ingestor (V1 – V6)

| Version | Description |
| --- | --- |
| V1 | Initial schema — documents, document_chunks |
| V2 | HNSW pgvector index |
| V3 | Webhook events |
| V4 | Connector sync pages, integration tokens |
| V5 | Semantic chunking metadata |
| V6 | Tenant isolation, S3 storage fields, PII flags |

---

## Deployment

### Docker

```bash
# Build service images
docker build -t documentation-bot:latest ./documentation-bot
docker build -t document-ingestor:latest ./document-ingestor

# Build frontend static assets
cd frontend && npm run build   # outputs to frontend/dist/
```

### Kubernetes (raw manifests)

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml          # edit values first
kubectl apply -f k8s/postgres-statefulset.yaml
kubectl apply -f k8s/redis-deployment.yaml
kubectl apply -f k8s/documentation-bot-deployment.yaml
kubectl apply -f k8s/document-ingestor-deployment.yaml
kubectl apply -f k8s/ingress.yaml
```

### Helm (recommended for production)

```bash
# Install
helm install doc-ai ./helm/doc-ai-system \
  --namespace doc-ai --create-namespace \
  --set global.imageRegistry=your-registry.io \
  --set secrets.openaiApiKey="sk-..." \
  --set secrets.jwtSecret="$(openssl rand -base64 64)" \
  --set secrets.dbPassword="$(openssl rand -base64 32)" \
  --set ingress.host=api.your-domain.com \
  --set config.corsAllowedOrigins="https://app.your-domain.com"

# Upgrade
helm upgrade doc-ai ./helm/doc-ai-system \
  --reuse-values \
  --set bot.image.tag=v1.2.0

# Scale
helm upgrade doc-ai ./helm/doc-ai-system \
  --reuse-values \
  --set bot.replicaCount=5
```

**Key `values.yaml` knobs:**

| Key | Default | Description |
| --- | --- | --- |
| `bot.replicaCount` | `2` | Bot pod count |
| `ingestor.replicaCount` | `2` | Ingestor pod count |
| `bot.hpa.maxReplicas` | `10` | Bot HPA ceiling |
| `ingestor.hpa.maxReplicas` | `6` | Ingestor HPA ceiling |
| `redis.enabled` | `true` | Deploy Redis (set `false` to use external) |
| `postgres.enabled` | `true` | Deploy PostgreSQL (set `false` to use managed DB) |
| `postgres.storageSize` | `50Gi` | PVC size for PostgreSQL |
| `config.storageType` | `s3` | `local` or `s3` |
| `ingress.host` | — | Public hostname (TLS auto-provisioned via cert-manager) |

---

## Monitoring

### Health endpoints

```text
GET /actuator/health           — combined liveness + readiness
GET /actuator/health/liveness  — liveness probe
GET /actuator/health/readiness — readiness probe
GET /actuator/info
```

### Prometheus metrics

```text
GET /actuator/prometheus
```

Key metrics:
- `http_server_requests_seconds` — latency histogram by endpoint
- `jvm_memory_used_bytes` — heap pressure
- `hikaricp_connections_active` — DB connection pool saturation
- `resilience4j_circuitbreaker_state` — LLM circuit breaker (0=CLOSED, 1=OPEN)
- `cache.gets{result="hit"}` / `cache.gets{result="miss"}` — Redis embedding cache

**Recommended Grafana dashboard imports:**

| Dashboard | Grafana ID |
| --- | --- |
| JVM Micrometer | 4701 |
| Spring Boot 3.x Statistics | 11378 |
| Redis Dashboard | 763 |

### Structured logging

All services emit JSON logs in production. Key fields: `timestamp`, `level`, `service`, `tenantId`, `userId`, `traceId`, `message`.

---

## Project Structure

```text
doc-ai-system/
│
├── documentation-bot/             Spring Boot 4 — chat, auth, intelligence
│   └── src/main/
│       ├── java/com/docai/bot/
│       │   ├── adapter/rest/      25+ REST controllers
│       │   ├── application/       50+ services
│       │   ├── config/            Security, JWT, tenant filters, rate limit
│       │   └── domain/            30+ JPA entities, 25+ repositories
│       └── resources/
│           ├── application.yml
│           └── db/migration/      V1–V12 Flyway scripts
│
├── document-ingestor/             Spring Boot 4 — ingestion pipeline
│   └── src/main/
│       ├── java/com/docai/ingestor/
│       │   ├── adapter/rest/      Upload, webhook, connector endpoints
│       │   ├── application/       Parsers, chunkers, embedder, storage, PII
│       │   ├── config/            JWT validation, async
│       │   ├── domain/            Entities, repositories
│       │   └── infrastructure/    Directory watcher
│       └── resources/
│           ├── application.yml
│           └── db/migration/      V1–V6 Flyway scripts
│
├── frontend/                      React 18 + Vite + Tailwind CSS
│   └── src/
│       ├── components/            25+ components (Chat, Admin, Bookmarks…)
│       ├── context/               AuthContext, BrandingContext
│       ├── services/              20+ typed API service modules
│       └── hooks/
│
├── slack-bot/                     Node.js — Slack Bolt integration
├── teams-bot/                     Node.js — Bot Framework integration
├── vscode-extension/              TypeScript — VS Code sidebar extension
├── browser-extension/             Chrome extension (Manifest v3)
│
├── k8s/                           Raw Kubernetes manifests
│   ├── namespace.yaml
│   ├── configmap.yaml
│   ├── secrets.yaml
│   ├── postgres-statefulset.yaml
│   ├── redis-deployment.yaml
│   ├── documentation-bot-deployment.yaml
│   ├── document-ingestor-deployment.yaml
│   └── ingress.yaml
│
└── helm/doc-ai-system/            Helm chart
    ├── Chart.yaml
    ├── values.yaml
    └── templates/                 8 templates (bot, ingestor, postgres, redis, ingress…)
```

---

## License

MIT
