# Document Ingestion

document-ingestor (port 8081) accepts documents from multiple sources, parses them, chunks the content, scans for PII, generates vector embeddings, and stores everything in PostgreSQL. Document binaries are stored in S3/MinIO.

---

## Supported Formats

| Extension | Notes |
|---|---|
| `.pdf` | Apache Tika extracts paragraph text; heading and table reconstruction is approximate |
| `.docx` | Full structure-preserving extraction |
| `.html`, `.htm` | Full structure-preserving extraction via HtmlToMarkdownConverter |
| `.chm` | Windows Compiled HTML Help — full structure preserved |
| `.txt`, `.md` | Plain text / Markdown |

---

## Ingestion Pipeline

```text
Source (upload / webhook / connector / directory watcher)
    │
    ▼
StructuredTextExtractor
    Apache Tika → XHTML → HtmlToMarkdownConverter → Markdown text
    │
    ▼
PiiDetectionService
    Scans for: SSN, credit card numbers, AWS access keys,
               email addresses, phone numbers, IP addresses
    Document with PII hits → status QUARANTINED (excluded from search)
    Admin notified; admin can approve ingestion or delete the document
    │
    ▼
SemanticChunker
    Structure-aware splitting: respects Markdown headings, tables, code blocks
    Target: 800 tokens per chunk, 100-token overlap
    Metadata extracted per chunk: section heading, TOC path, chunk type
    │
    ▼
EmbeddingService
    OpenAI text-embedding-3-small → 1536-dimensional float vectors
    │
    ▼
completeIngestion() [@Transactional]
    Inserts all chunks atomically
    Flips document status to COMPLETED
    Retires superseded versions of the same document (previous embeddings tombstoned)
    PostgreSQL NOTIFY on 'docai_ingestion_completed' channel
    │
    ▼
document-bot receives NOTIFY, refreshes any cached scopes
```

**Stuck-document reaper:** A `@Scheduled` job runs every 5 minutes and marks documents stuck in `PROCESSING` for longer than `STUCK_PROCESSING_TIMEOUT_MINUTES` (default: 30) as `FAILED`. Check the ingestor logs for the root cause.

---

## Upload a Document

```bash
curl -X POST http://localhost:8081/api/ingest \
  -H "Authorization: Bearer <jwt>" \
  -F "file=@installation-guide.pdf" \
  -F "product=MyProduct" \
  -F "version=2.1.0" \
  -F "documentName=Installation Guide"   # optional, defaults to filename
```

Response:

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "name": "Installation Guide",
  "status": "PENDING",
  "product": "MyProduct",
  "version": "2.1.0"
}
```

### Poll status

```bash
curl http://localhost:8081/api/documents/<documentId> \
  -H "Authorization: Bearer <jwt>"
```

Status values: `PENDING → PROCESSING → COMPLETED` or `FAILED` or `QUARANTINED`.

### List all documents

```bash
curl "http://localhost:8081/api/documents?product=MyProduct&version=2.1.0" \
  -H "Authorization: Bearer <jwt>"
```

### Delete a document

```bash
curl -X DELETE http://localhost:8081/api/documents/<documentId> \
  -H "Authorization: Bearer <jwt>"
```

This deletes the document, all its chunks, and the S3 binary.

---

## Fetch by URL

Ingest a document hosted at a URL instead of uploading a file:

```bash
curl -X POST http://localhost:8081/api/ingest/url \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://docs.example.com/guide.html",
    "product": "MyProduct",
    "version": "2.1.0"
  }'
```

`SafeUrlValidator` enforces: HTTPS-only, no private IP ranges, max 3 redirect hops. See [Security — SSRF Protection](security.md#ssrf-protection).

---

## CI/CD Webhook Ingestion

Trigger re-ingestion automatically when documentation is published in your build pipeline.

### Register a webhook

```bash
curl -X POST http://localhost:8081/api/webhooks \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{
    "product": "MyProduct",
    "version": "2.1.0",
    "secret": "my-webhook-hmac-secret"
  }'
# Returns: {"webhookId":"...","signingSecret":"..."}
```

### Trigger ingestion from your pipeline

```bash
# Compute HMAC-SHA-256 of the request body using the signing secret
BODY='{"url":"https://docs.example.com/guide.html","product":"MyProduct","version":"2.1.0"}'
SIG="sha256=$(echo -n "$BODY" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" | awk '{print $2}')"

curl -X POST "http://localhost:8081/api/webhooks/<webhookId>/ingest" \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Signature: $SIG" \
  -d "$BODY"
```

Alternatively, set `WEBHOOK_HMAC_SECRET` in the ingestor environment and use `POST /api/v1/ingest/webhook` with a platform-wide secret instead of per-webhook secrets.

---

## Source Connectors

### Confluence Cloud

```bash
# Register a Confluence integration (stores token encrypted at rest)
curl -X POST http://localhost:8081/api/connectors/confluence \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{
    "siteUrl": "https://your-org.atlassian.net",
    "apiToken": "<confluence-api-token>",
    "email": "admin@your-org.com"
  }'

# Sync a space
curl -X POST http://localhost:8081/api/connectors/confluence/sync \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{
    "spaceKey": "ENG",
    "product": "MyProduct",
    "version": "latest"
  }'
```

Confluence site URLs must match the `CONFLUENCE_HOST_ALLOWLIST` suffix (default: `.atlassian.net`).

### GitHub / GitLab Webhook

Register a repository webhook to auto-ingest documentation files on push:

```bash
curl -X POST http://localhost:8081/api/webhooks/github \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{
    "repoUrl": "https://github.com/your-org/your-docs-repo",
    "branch": "main",
    "pathPattern": "docs/**/*.md",
    "product": "MyProduct",
    "version": "latest",
    "secret": "<github-webhook-secret>"
  }'
```

Configure the returned endpoint URL as your GitHub repository's webhook (push events only).

### Directory Watcher

The ingestor monitors a configurable directory (`/app/watched-docs` inside the container) and automatically ingests files dropped there. Use for batch imports:

```bash
# Drop a file directly into the container's watched directory
docker cp my-guide.pdf docai-ingestor:/app/watched-docs/
```

---

## Chunking Strategies

Semantic chunking is the default and only strategy since v6.

**How SemanticChunker works:**

1. Parses Markdown headings, tables, and code blocks as structural boundaries.
2. Splits at natural boundaries rather than arbitrary token counts.
3. Targets 800 tokens per chunk with 100-token overlap between adjacent chunks.
4. Records per-chunk metadata: section heading, TOC path, `chunk_type` (`PROSE`, `CODE`, `TABLE`).

This metadata is used at retrieval time to:
- Weight code-block chunks higher for code-style queries
- Return the section heading as part of the citation
- Enable hierarchical retrieval (search small, return parent context)

---

## PII Handling

`PiiDetectionService` scans document text before chunking for:

| Type | Pattern |
|---|---|
| Social Security Numbers | `\d{3}-\d{2}-\d{4}` |
| Credit card numbers | Luhn-valid 13–19 digit sequences |
| AWS access keys | `AKIA[0-9A-Z]{16}` |
| Email addresses | RFC-compliant pattern |
| Phone numbers | US and international formats |
| IP addresses | IPv4 and IPv6 |

Documents with hits are set to `QUARANTINED` status and are excluded from all search results. An admin notification is created. The tenant ADMIN can:

- Approve ingestion if the PII was expected (e.g. an internal auth guide)
- Delete the document and re-upload a redacted version

```bash
# Approve a quarantined document
curl -X POST http://localhost:8081/api/documents/<documentId>/approve-quarantine \
  -H "Authorization: Bearer <admin-jwt>"
```

---

## Version Supersession

When a document is re-ingested with the same `product` and `version` values as an existing document:

1. The new document is ingested and all its chunks stored atomically.
2. The previous document's chunks are marked as superseded (tombstoned) — they no longer appear in search results.
3. The previous document record is retained for audit purposes.

This ensures searches always reflect the latest version of a document without a window of inconsistency.
