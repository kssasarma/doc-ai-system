# Docs-inator — Full Stack Setup Guide

> This guide covers everything needed to get the full stack running locally and share it as a company website. Slack, Teams, and VS Code integrations are out of scope.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Project Overview](#2-project-overview)
3. [Environment Configuration](#3-environment-configuration)
4. [Build and Start the Stack](#4-build-and-start-the-stack)
5. [First-Time Setup: Create an Admin Account](#5-first-time-setup-create-an-admin-account)
6. [Upload Your First Document](#6-upload-your-first-document)
7. [Use the Web UI](#7-use-the-web-ui)
8. [API Quick Reference](#8-api-quick-reference)
9. [Publishing as a Company Website](#9-publishing-as-a-company-website)
10. [Useful Operations](#10-useful-operations)
11. [Troubleshooting](#11-troubleshooting)

---

## 1. Prerequisites

| Tool | Minimum version | Check |
|------|----------------|-------|
| Docker Desktop | 24+ | `docker version` |
| Docker Compose | v2.20+ | `docker compose version` |
| An LLM API key | — | OpenAI, or any OpenAI-compatible endpoint |

No Java, Node.js, or Maven installation is needed — everything compiles inside Docker.

---

## 2. Project Overview

```
┌─────────────────────────────────────────────────────────┐
│  Browser  →  http://localhost:3000  (React frontend)    │
│               │                                         │
│               ├─→ http://localhost:8082  (bot API)      │
│               │     • Auth (register / login / JWT)     │
│               │     • AI chat (vector search + LLM)     │
│               │     • Admin panel                       │
│               │                                         │
│               └─→ http://localhost:8081  (ingestor)     │
│                     • Document upload (PDF / CHM)       │
│                     • Parsing, chunking, embedding      │
│                                                         │
│  Postgres + pgvector  ←  both Java services            │
│  Redis                ←  rate-limit cache (bot only)   │
└─────────────────────────────────────────────────────────┘
```

| Container | Port | Image |
|-----------|------|-------|
| `docai-postgres` | 5432 | `pgvector/pgvector:pg16` |
| `docai-redis` | 6379 | `redis:7-alpine` |
| `docai-ingestor` | 8081 | Built from `./document-ingestor` |
| `docai-bot` | 8082 | Built from `./documentation-bot` |
| `docai-frontend` | 3000 | Built from `./frontend` |

Slack and Teams bots exist in the repo but use a Docker Compose `bots` profile and are **never started** unless you explicitly pass `--profile bots`.

---

## 3. Environment Configuration

### 3.1 Create your `.env` file

```powershell
cd C:\GitHubProjects\doc-ai-system
Copy-Item .env.example .env
```

Then open `.env` and fill in the values below. Never commit this file.

### 3.2 Required values

```dotenv
# ── Database ──────────────────────────────────────────────────────────────
POSTGRES_DB=docai
POSTGRES_USER=docai
POSTGRES_PASSWORD=change_me_strong_password          # pick something real

# ── Spring datasource (used by both Java services) ────────────────────────
SPRING_DATABASE_URL=jdbc:postgresql://postgres:5432/docai
SPRING_DATABASE_USERNAME=docai
SPRING_DATABASE_PASSWORD=change_me_strong_password   # must match above

# ── LLM (OpenAI or any OpenAI-compatible endpoint) ────────────────────────
OPENAI_API_KEY=sk-...
OPENAI_BASE_URL=https://api.openai.com/v1            # or your internal broker
OPENAI_CHAT_MODEL=gpt-4o-mini                        # chat completions model
OPENAI_EMBEDDING_MODEL=text-embedding-3-small        # must produce float vectors

# ── Security ──────────────────────────────────────────────────────────────
# Generate with PowerShell:
#   [Convert]::ToBase64String((1..64 | % { Get-Random -Max 256 }))
JWT_SECRET=<base64-encoded-64-byte-random-string>
JWT_EXPIRATION_MS=86400000                           # 24 hours

# ── CORS ──────────────────────────────────────────────────────────────────
# For local testing:
CORS_ALLOWED_ORIGINS=http://localhost:3000
# For a specific domain later:
# CORS_ALLOWED_ORIGINS=https://docs.yourcompany.com

# ── Rate limiting ─────────────────────────────────────────────────────────
RATE_LIMIT_PER_MINUTE=30

# ── Frontend build URLs (must be reachable from the browser) ─────────────
VITE_BACKEND_URL=http://localhost:8082
VITE_INGESTOR_URL=http://localhost:8081
```

> **Note on `VITE_*` variables:** These are baked into the frontend at build time by Vite. If you change the server address later you must rebuild the frontend image (`docker compose build frontend`).

---

## 4. Build and Start the Stack

### 4.1 First build (downloads all Maven and npm dependencies — ~5–10 min)

```powershell
cd C:\GitHubProjects\doc-ai-system
docker compose --env-file .env up -d --build
```

This starts **only** the 5 core containers. The bots profile is not activated.

### 4.2 Watch the startup

The Java services need about 90 seconds to start after the image is built.

```powershell
# Stream all logs
docker compose logs -f

# Or watch a specific service
docker compose logs -f documentation-bot
docker compose logs -f document-ingestor
```

### 4.3 Confirm everything is healthy

```powershell
docker compose ps
```

Expected output (all rows should show `Up` or `healthy`):

```
NAME               STATUS
docai-postgres     Up (healthy)
docai-redis        Up (healthy)
docai-ingestor     Up (healthy)
docai-bot          Up (healthy)
docai-frontend     Up
```

### 4.4 Health-check the APIs directly

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health   # ingestor
Invoke-RestMethod http://localhost:8082/actuator/health   # bot
```

Both should return `{"status":"UP"}`.

---

## 5. First-Time Setup: Create an Admin Account

Users who register through the API or UI get the `USER` role by default. The document upload endpoint requires `ADMIN`. Do this once after first boot.

### 5.1 Register

```powershell
Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8082/api/auth/register" `
  -ContentType "application/json" `
  -Body '{"username":"admin","email":"admin@yourcompany.com","password":"Admin1234!"}'
```

Password must be at least 6 characters.

### 5.2 Promote to ADMIN in the database

```powershell
docker exec -it docai-postgres psql -U docai -d docai `
  -c "UPDATE users SET role = 'ADMIN' WHERE username = 'admin';"
```

### 5.3 Log in and capture the JWT token

```powershell
$r = Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8082/api/auth/login" `
  -ContentType "application/json" `
  -Body '{"username":"admin","password":"Admin1234!"}'

$TOKEN = $r.token
Write-Host "Token captured. Expires in 24 h."
```

Keep `$TOKEN` in your shell session — you will use it for uploads. Tokens are valid for 24 hours (configurable via `JWT_EXPIRATION_MS`).

---

## 6. Upload Your First Document

### 6.1 Supported formats

| Format | Notes |
|--------|-------|
| `.pdf` | Full text extraction |
| `.chm` | Windows Compiled HTML Help |

Other formats (`.txt`, `.html`, `.md`) can be dropped into the watched directory (see §6.3) but the upload API only accepts PDF and CHM.

### 6.2 Upload via API

```powershell
$headers = @{ Authorization = "Bearer $TOKEN" }

Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8081/api/documents/upload" `
  -Headers $headers `
  -Form @{
      file         = Get-Item "C:\path\to\your-document.pdf"
      product      = "MyProduct"
      version      = "1.0"
      documentName = "User Guide"       # optional, defaults to filename
  }
```

**`product` and `version`** are free-text metadata tags. They let the AI filter answers to a specific product version when a user asks "In version 2.0, how do I…?". Use whatever naming makes sense for your team.

Expected response:

```json
{
  "id": "3fa85f64-...",
  "name": "User Guide",
  "status": "PENDING",
  "product": "MyProduct",
  "version": "1.0"
}
```

### 6.3 Watched directory (drop-folder alternative)

Any file placed in the Docker volume `ingestor_watched` is picked up automatically every 5 seconds:

```powershell
# Copy a file directly into the ingestor container's watched folder
docker cp "C:\path\to\your-document.pdf" docai-ingestor:/app/watched-docs/
```

### 6.4 Check ingestion status

```powershell
# List all documents and their status
Invoke-RestMethod -Uri "http://localhost:8081/api/documents" `
  -Headers @{ Authorization = "Bearer $TOKEN" }
```

The `status` field cycles: `PENDING` → `PROCESSING` → `COMPLETED` (or `FAILED`).

Processing time depends on document size and LLM embedding latency — typically 10–60 seconds per document.

```powershell
# Overall ingestion summary
Invoke-RestMethod -Uri "http://localhost:8081/api/ingest/status" `
  -Headers @{ Authorization = "Bearer $TOKEN" }
```

---

## 7. Use the Web UI

### 7.1 Open the app

Navigate to **http://localhost:3000** in any browser.

The root path redirects to `/docs-inator/`. You will see the login page.

### 7.2 Register and log in

- Click **Register** → fill in username, email, and password
- Click **Login** → use the credentials you just created
- Regular users can chat immediately once documents are ingested

### 7.3 Chat

1. Click **New Chat** in the sidebar
2. Type a question about any document you have uploaded
3. The AI retrieves the most relevant chunks via vector search and generates an answer with source references

### 7.4 Admin panel

Log in with the `admin` account → click the **Admin** menu item in the sidebar.

From the admin panel you can:

| Section | What you can do |
|---------|----------------|
| Documents | See all uploads, re-trigger ingestion, delete documents |
| Analytics | Queries per day, top questions, unanswered queries |
| Users | List all accounts, manage roles |
| Gap Reports | Questions the AI could not answer well |
| FAQ | Auto-generated FAQ entries from real questions |
| Audit Log | Immutable log of all admin actions |

---

## 8. API Quick Reference

Both services expose a Swagger UI:

- Ingestor: http://localhost:8081/swagger-ui.html
- Bot: http://localhost:8082/swagger-ui.html

### Authentication

All protected endpoints accept a JWT in the `Authorization` header:

```
Authorization: Bearer <token>
```

Obtain a token via `POST /api/auth/login` on port 8082. The same token is valid on **both** port 8081 and 8082 (shared JWT secret).

### Key endpoints

#### Auth (port 8082)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/auth/register` | None | Create account |
| `POST` | `/api/auth/login` | None | Get JWT token |
| `GET` | `/api/auth/me` | User | Current user info |

#### Chat (port 8082)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/chat/query` | User | Send a question, get an AI answer |
| `GET` | `/api/chat` | User | List all chat sessions |
| `GET` | `/api/chat/{chatId}` | User | Get chat history |
| `DELETE` | `/api/chat/{chatId}` | User | Delete a session |

**Chat request body:**

```json
{
  "question": "How do I configure the firewall?",
  "chatId": "optional-existing-session-id",
  "product": "MyProduct",
  "version": "1.0"
}
```

#### Documents (port 8081 — ADMIN only)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/documents/upload` | Admin | Upload a PDF or CHM |
| `GET` | `/api/documents` | Admin | List all documents |
| `DELETE` | `/api/documents/{id}` | Admin | Delete a document |
| `GET` | `/api/ingest/status` | Admin | Ingestion summary |

#### Programmatic API with API keys (port 8082)

You can also authenticate without JWT using an API key (useful for CI/CD or scripts):

```powershell
# 1. Create an API key while logged in as admin
Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8082/api/keys" `
  -ContentType "application/json" `
  -Headers @{ Authorization = "Bearer $TOKEN" } `
  -Body '{"name":"my-script-key","expiresInDays":90}'

# 2. Use the returned key in subsequent requests
$API_KEY = "<key-from-above>"
Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8082/api/v1/query" `
  -ContentType "application/json" `
  -Headers @{ "X-API-Key" = $API_KEY } `
  -Body '{"question":"What is the max file size for uploads?"}'
```

---

## 9. Publishing as a Company Website

### Option A — Expose the local machine on the company network

Fastest for internal testing with no server setup:

1. Open port 3000 in Windows Firewall:
   ```powershell
   New-NetFirewallRule -DisplayName "Docs-inator" -Direction Inbound `
     -LocalPort 3000 -Protocol TCP -Action Allow
   ```
2. Find your machine's IP:
   ```powershell
   (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike "127.*" }).IPAddress
   ```
3. Colleagues access the app at `http://YOUR_IP:3000`

> The frontend talks to `localhost:8082` and `localhost:8081` — those API calls come from the **browser**, so colleagues on other machines will fail unless you also expose ports 8081 and 8082. For a proper deployment, use Option B.

### Option B — Deploy on a dedicated server (recommended)

This is the correct approach for a stable company URL.

#### Step 1 — Copy the project to the server

```bash
scp -r ./doc-ai-system user@your-server:/opt/doc-ai-system
# or: git clone your repo on the server
```

#### Step 2 — Update `.env` on the server

```dotenv
# Replace with your actual domain or server IP
CORS_ALLOWED_ORIGINS=http://docs.yourcompany.com

VITE_BACKEND_URL=http://docs.yourcompany.com:8082
VITE_INGESTOR_URL=http://docs.yourcompany.com:8081
```

#### Step 3 — Build and start

```bash
cd /opt/doc-ai-system
docker compose --env-file .env up -d --build
```

#### Step 4 — Point DNS

In your DNS provider, add an `A` record:

```
docs.yourcompany.com  →  <server public IP>
```

#### Step 5 — (Optional) Add TLS with nginx or Caddy in front

For HTTPS, put a reverse proxy in front that terminates TLS and forwards:

- `https://docs.yourcompany.com` → `http://localhost:3000`
- `https://docs.yourcompany.com:8082` → `http://localhost:8082` (or sub-path proxy)
- `https://docs.yourcompany.com:8081` → `http://localhost:8081`

Update `CORS_ALLOWED_ORIGINS`, `VITE_BACKEND_URL`, and `VITE_INGESTOR_URL` to use `https://` URLs, then rebuild the frontend.

---

## 10. Useful Operations

### Restart a single service

```powershell
docker compose restart documentation-bot
docker compose restart document-ingestor
```

### Stop everything (keep data)

```powershell
docker compose down
```

### Stop and wipe all data (full reset)

```powershell
docker compose down -v   # WARNING: deletes postgres and upload volumes
```

### Rebuild after a code change

```powershell
# Rebuild and restart just the changed service
docker compose up -d --build documentation-bot

# Rebuild all
docker compose up -d --build
```

### View logs for a service

```powershell
docker compose logs -f documentation-bot --tail=100
docker compose logs -f document-ingestor --tail=100
docker compose logs -f docai-frontend
```

### Connect to the database directly

```powershell
docker exec -it docai-postgres psql -U docai -d docai
```

Useful queries:

```sql
-- List all users and their roles
SELECT username, email, role, created_at FROM users ORDER BY created_at;

-- Promote a user to ADMIN
UPDATE users SET role = 'ADMIN' WHERE username = 'alice';

-- List all ingested documents and their status
SELECT name, product, version, status, chunk_count FROM documents ORDER BY created_at DESC;

-- Count vector chunks
SELECT COUNT(*) FROM document_chunks;
```

### Promote a user to ADMIN (quick one-liner)

```powershell
docker exec docai-postgres psql -U docai -d docai `
  -c "UPDATE users SET role='ADMIN' WHERE username='<username>';"
```

---

## 11. Troubleshooting

### Container keeps restarting

```powershell
docker compose logs <container-name> --tail=50
```

Common causes:

| Service | Symptom | Cause |
|---------|---------|-------|
| `docai-bot` | `Could not connect to Redis` | Redis not healthy yet; it will retry |
| `docai-bot` | `FlywayException: validate failed` | DB schema mismatch; check migration files |
| `docai-ingestor` | `Connection refused` on postgres | Postgres still starting; wait for healthy |
| Any Java service | `Invalid JWT secret` | `JWT_SECRET` is too short (must be ≥ 32 chars) |

### 401 Unauthorized on API calls

- Token has expired (default: 24 h) — log in again to get a new token
- Check `Authorization: Bearer <token>` header — note the space after `Bearer`

### 403 Forbidden on document upload

The account does not have the `ADMIN` role. Run the promotion query from §5.2.

### Ingestion stuck at PROCESSING

```powershell
docker compose logs document-ingestor --tail=100
```

Most likely cause: the LLM embedding API returned an error (wrong model name, quota exceeded, or network issue). The ingestor will retry automatically. You can also delete the stuck document and re-upload:

```powershell
Invoke-RestMethod -Method DELETE `
  -Uri "http://localhost:8081/api/documents/<id>" `
  -Headers @{ Authorization = "Bearer $TOKEN" }
```

### Frontend shows a blank white page

```powershell
docker compose logs docai-frontend
```

Usually a build-time issue where `VITE_BACKEND_URL` or `VITE_INGESTOR_URL` was not set correctly. Fix the `.env`, then:

```powershell
docker compose build frontend
docker compose up -d frontend
```

### Port already in use

```powershell
netstat -ano | findstr ":3000"
netstat -ano | findstr ":8081"
netstat -ano | findstr ":8082"
```

Stop the conflicting process or change the host port mapping in `docker-compose.yml`.

### Reset a single user's password

```powershell
# The password is BCrypt-hashed. Use the register endpoint or update via SQL.
# Easiest: delete and re-register the account.
docker exec docai-postgres psql -U docai -d docai `
  -c "DELETE FROM users WHERE username = 'alice';"
```

Then re-register via `POST /api/auth/register`.
