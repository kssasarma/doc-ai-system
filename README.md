# Docs-inator — AI Documentation Intelligence Platform

> Ask questions in plain English. Get answers grounded in your product documentation, your version, and your team's institutional knowledge — not the open web.

Docs-inator is a self-hosted, multi-tenant RAG (Retrieval-Augmented Generation) platform. Teams upload documentation — PDFs, Word docs, HTML, CHM files, Confluence spaces, GitHub repos — and end-users get precise, cited answers from an LLM that can only draw on that documentation.

---

## How It Works

```
User question
     │
     ▼
TenantResolutionFilter → RateLimitFilter → Auth (JWT / API Key / OIDC)
     │
     ▼
VectorSearchService ──── hybrid retrieval ────▶ PostgreSQL + pgvector
  dense (cosine)                                  (HNSW index)
  lexical (tsvector)
  RRF fusion + MMR re-ranking
     │
     ▼
AnswerGenerationService ──▶ LLMRouter ──▶ OpenAI / Anthropic
     │                         (per-tenant config, circuit breaker,
     │                          automatic fallback)
     ▼
Cited answer + confidence score + follow-up suggestions
```

---

## Quick Start

The fastest path to a running system is Docker Compose. All services — PostgreSQL with pgvector, Redis, MinIO, the two Spring Boot services, and the React frontend — start with one command.

```bash
git clone <repo-url> && cd doc-ai-system
cp .env.example .env
# Fill in OPENAI_API_KEY and the three required secrets (see .env.example)
docker compose up -d --build
```

Open **http://localhost:3000** once all containers are healthy (~90 s for the Java services).

For a full walkthrough — bare metal, first login, uploading your first document — see **[Getting Started](docs/getting-started.md)**.

---

## Feature Highlights

| Category | Features |
|---|---|
| **RAG pipeline** | Hybrid dense + lexical search, RRF fusion, MMR re-ranking, semantic chunking, HNSW index |
| **Intelligence** | Multi-hop reasoning, version diff, auto-FAQ, People Also Asked, answer evolution timeline |
| **Auth** | JWT, API keys (`dak_` prefix), OIDC/SSO JIT provisioning, invitation-only signup |
| **Multi-tenancy** | Row-level isolation, per-tenant LLM config, BYO API keys (AES-256-GCM at rest) |
| **Integrations** | Slack bot, Teams bot, Chrome/Edge extension, VS Code extension, CI/CD webhook ingestion |
| **Source connectors** | Confluence Cloud, Notion, GitHub/GitLab webhook, directory watcher |
| **Admin** | Analytics dashboard, documentation gap reports, cost tracking, escalation workflow |
| **Compliance** | GDPR Art. 17 erasure + Art. 20 export, PII detection, data retention policies, full audit log |
| **Deployment** | Docker Compose, Kubernetes raw manifests, Helm chart, nightly pg_dump backup CronJob |

---

## Services at a Glance

| Service | Port | Stack | Role |
|---|---|---|---|
| **documentation-bot** | 8082 | Spring Boot 4, Java 21 | Chat, auth, intelligence, admin APIs |
| **document-ingestor** | 8081 | Spring Boot 4, Java 21 | Ingestion pipeline, embeddings, storage |
| **frontend** | 3000 | React 18, Vite, TypeScript, Tailwind | Web UI |
| **PostgreSQL + pgvector** | 5432 | pgvector:pg16 | Vector + relational store |
| **Redis** | 6379 | redis:7-alpine | Embedding cache, rate-limit counters |
| **MinIO** | 9000/9001 | minio/minio | S3-compatible object store (dev only) |

Both Java services share one PostgreSQL database; their Flyway migration histories are kept in separate tables so they can evolve independently.

---

## Documentation

| Page | What's in it |
|---|---|
| [Getting Started](docs/getting-started.md) | Prerequisites, Docker Compose quick start, bare-metal setup, first login walkthrough |
| [Architecture](docs/architecture.md) | System diagram, RAG query flow, service responsibilities, key design decisions |
| [Configuration](docs/configuration.md) | Every environment variable for both services, with defaults and descriptions |
| [Authentication](docs/authentication.md) | JWT flow, API key creation, OIDC/SSO setup, invitation-only signup |
| [Multi-Tenancy](docs/multi-tenancy.md) | Tenant model, roles, tenant resolution, creating and managing tenants |
| [Document Ingestion](docs/document-ingestion.md) | Ingestion pipeline, supported formats, chunking, PII detection, webhooks, connectors |
| [API Reference](docs/api-reference.md) | Complete endpoint tables for both services, Swagger UI links |
| [Integrations](docs/integrations.md) | Slack bot, Teams bot, browser extension, VS Code extension setup guides |
| [Deployment](docs/deployment.md) | Production Docker Compose, Kubernetes, Helm chart, database backups and restore |
| [Security](docs/security.md) | SSRF protection, HMAC verification, GDPR, PII, rate limiting, secrets management |
| [Database](docs/database.md) | Schema overview, Flyway migration history for both services |
| [Monitoring](docs/monitoring.md) | Health endpoints, Prometheus metrics, structured logging, Grafana dashboards |

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Docker | 24+ | Required for the full stack |
| Docker Compose | v2.20+ | `docker compose version` |
| OpenAI API key | — | Required for embeddings and chat |
| Java 21 + Maven 3.9+ | — | Only needed for bare-metal development |
| Node.js 20+ | — | Only needed for frontend or bot development |

**Optional:**

| Tool | Enables |
|---|---|
| Anthropic API key | Claude as an alternate LLM for any tenant |
| Redis | Embedding cache (degrades gracefully without it) |
| SMTP credentials | Email delivery for invitations and digests |

---

## License

MIT
