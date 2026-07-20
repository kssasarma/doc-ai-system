# Getting Started

This guide takes you from zero to a working Docs-inator stack with your first document ingested and a chat session open.

---

## Prerequisites

| Tool | Minimum version | How to check |
|---|---|---|
| Docker | 24+ | `docker version` |
| Docker Compose | v2.20+ | `docker compose version` |
| OpenAI API key | — | Required for embeddings and chat |
| Java 21 + Maven 3.9+ | — | Bare-metal only — not needed for Docker path |
| Node.js 20+ | — | Bare-metal frontend / bot dev only |

---

## Path A — Docker Compose (recommended)

The entire stack — PostgreSQL with pgvector, Redis, MinIO, both Java services, and the React frontend — starts with one command.

### 1. Clone and configure

```bash
git clone <repo-url>
cd doc-ai-system
cp .env.example .env
```

Open `.env` and fill in these required values:

```dotenv
OPENAI_API_KEY=sk-...

# Generate with: openssl rand -base64 64
JWT_SECRET=<base64-64-byte-random>

# Generate with: openssl rand -base64 32  (must be exactly 32 bytes decoded)
SECRETS_ENCRYPTION_KEY=<base64-32-byte-random>

# Pick something strong — you will be forced to change it on first login
SEED_ADMIN_PASSWORD=<initial-superadmin-password>
```

Everything else in `.env.example` has working defaults for local development. See [Configuration](configuration.md) for the full reference.

### 2. Start the stack

```bash
docker compose up -d --build
```

The first build downloads all Maven and npm dependencies (~5–10 min). Subsequent starts are fast.

### 3. Wait for healthy containers

```bash
docker compose ps
```

All five core containers should show `Up (healthy)` or `Up`:

```
NAME                STATUS
docai-postgres      Up (healthy)
docai-redis         Up (healthy)
docai-minio         Up
docai-ingestor      Up (healthy)
docai-bot           Up (healthy)
docai-frontend      Up
```

The Java services take about 90 seconds to finish starting after the images are built. Stream logs with:

```bash
docker compose logs -f documentation-bot document-ingestor
```

### 4. Verify the APIs

```bash
curl http://localhost:8081/actuator/health   # ingestor → {"status":"UP"}
curl http://localhost:8082/actuator/health   # bot     → {"status":"UP"}
```

### 5. Open the web UI

Navigate to **http://localhost:3000**. You will see the login page.

---

## Path B — Bare Metal

Use this if you want to develop or debug individual services without Docker.

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

### 2. Start MinIO (required by document-ingestor)

document-ingestor has no local filesystem fallback — it always writes to S3-compatible storage.

```bash
docker run -d \
  --name docai-minio \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin123 \
  -p 9000:9000 -p 9001:9001 \
  minio/minio server /data --console-address ":9001"

# Create the default bucket
docker run --rm --network host --entrypoint sh minio/mc -c "
  mc alias set local http://localhost:9000 minioadmin minioadmin123 &&
  mc mb -p local/docai-documents
"
```

### 3. Start document-ingestor

```bash
cd document-ingestor
export OPENAI_API_KEY=sk-...
export S3_ENDPOINT=http://localhost:9000   # override the docker-compose hostname
./mvnw spring-boot:run
# Listening on http://localhost:8081
```

### 4. Start documentation-bot

```bash
cd documentation-bot
export OPENAI_API_KEY=sk-...
export JWT_SECRET=$(openssl rand -base64 64)
./mvnw spring-boot:run
# Listening on http://localhost:8082
```

### 5. Start the frontend

```bash
cd frontend
npm install
npm run dev
# Opens http://localhost:5173/docs-inator
```

> **Note:** In bare-metal mode the frontend runs on port 5173, not 3000. `VITE_BACKEND_URL` and `VITE_INGESTOR_URL` default to `localhost:8082` and `localhost:8081` — that's correct for local development.

---

## First Login and Tenant Setup

### The seeded SUPER_ADMIN account

On first startup (empty `users` table), `AdminSeeder` creates one SUPER_ADMIN account:

| Field | Default |
|---|---|
| Username | `admin` |
| Password | `Opentext123$` (or `SEED_ADMIN_PASSWORD` if you set it) |
| Role | `SUPER_ADMIN` |

This account has no tenant of its own — it exists only to create tenants and invite each one's first ADMIN. It is forced to change its password on first use.

### 1. Log in and change the password

```bash
TOKEN=$(curl -s -X POST http://localhost:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Opentext123$"}' | jq -r .token)

curl -s -X POST http://localhost:8082/api/auth/change-password \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"currentPassword":"Opentext123$","newPassword":"<new-password>"}'
```

### 2. Create your first tenant

```bash
curl -s -X POST http://localhost:8082/api/admin/tenants \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Acme Corp",
    "slug": "acme",
    "plan": "standard",
    "maxUsers": 50,
    "maxDocuments": 500,
    "adminEmail": "you@example.com"
  }'
```

This creates an invitation for `adminEmail` to become the tenant's first ADMIN. If SMTP is configured, the invite arrives by email. Without SMTP the invite token exists in the database but cannot be delivered — see [Configuration](configuration.md) for SMTP variables.

### 3. Accept the invitation

```bash
curl -s -X POST http://localhost:8082/api/auth/accept-invite \
  -H "Content-Type: application/json" \
  -d '{
    "token": "<token-from-email>",
    "username": "alice",
    "password": "<password>"
  }'
```

You now have a tenant ADMIN account. Log in as `alice` through the web UI or API.

---

## Upload Your First Document

```bash
# Log in as the tenant admin
TOKEN=$(curl -s -X POST http://localhost:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"<password>"}' | jq -r .token)

# Upload a PDF
curl -X POST http://localhost:8081/api/ingest \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@installation-guide.pdf" \
  -F "product=MyProduct" \
  -F "version=2.1.0"
```

Supported formats: `.pdf`, `.docx`, `.html`, `.htm`, `.chm`, `.txt`, `.md`

The ingestion pipeline runs asynchronously: parse → chunk → PII scan → embed → store. Poll the status:

```bash
curl http://localhost:8081/api/documents \
  -H "Authorization: Bearer $TOKEN"
```

Status cycles: `PENDING → PROCESSING → COMPLETED` (or `FAILED`). Typical time: 10–60 seconds depending on document size.

---

## Ask Your First Question

Open **http://localhost:3000**, log in as `alice`, click **New Chat**, and type a question about the document you just uploaded. The AI retrieves the most relevant chunks via hybrid vector search and generates a cited answer.

---

## Common Operations

### Restart a single service

```bash
docker compose restart documentation-bot
```

### Rebuild after a code change

```bash
docker compose up -d --build documentation-bot
```

### Stop everything (keep data)

```bash
docker compose down
```

### Full reset (wipes all data volumes)

```bash
docker compose down -v
```

### Stream logs for a service

```bash
docker compose logs -f documentation-bot --tail=100
```

### Connect to PostgreSQL

```bash
docker exec -it docai-postgres psql -U docai -d docai
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Bot container restarts immediately | `JWT_SECRET` too short (must decode to ≥32 bytes) | Regenerate with `openssl rand -base64 64` |
| `FlywayException: validate failed` | Migration mismatch | Check that no migration SQL was manually edited |
| Document stuck at `PROCESSING` for >30 min | Embedding API error or quota exhaustion | Check ingestor logs; the reaper job will mark it `FAILED` automatically |
| Frontend blank white page | `VITE_BACKEND_URL` wrong at build time | Fix `.env`, then `docker compose build frontend && docker compose up -d frontend` |
| `401` on API calls | Token expired (default 24 h) | Log in again |
| `403` on document upload | Account lacks tenant ADMIN role | Invite flow must be completed; SUPER_ADMIN cannot upload directly |
| Port already in use | Conflict with another local service | Change host port in `docker-compose.yml` |

For detailed logs:

```bash
docker compose logs <service-name> --tail=100
```

---

**Next:** [Architecture](architecture.md) — understand how all the pieces fit together.
