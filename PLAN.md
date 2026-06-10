# Doc-AI System — Production Roadmap
## From Working MVP to Market-Leading Documentation Intelligence Platform

> **Vision**: Every developer, support engineer, and technical writer using versioned product documentation should reach for Docs-inator the same way they reach for Google — except the answers are always grounded in *your* documentation, *your* version, and *your* team's institutional knowledge.

---

## Table of Contents

1. [Current State Honest Assessment](#1-current-state-honest-assessment)
2. [Phase 0 — Production Foundation (Fix Before You Grow)](#phase-0--production-foundation)
3. [Phase 1 — Trust & Accuracy (Why Users Stay)](#phase-1--trust--accuracy)
4. [Phase 2 — Individual Stickiness (Why Users Come Back Daily)](#phase-2--individual-stickiness)
5. [Phase 3 — Team Collaboration (Network Effects)](#phase-3--team-collaboration)
6. [Phase 4 — Admin & Analytics Intelligence](#phase-4--admin--analytics-intelligence)
7. [Phase 5 — Integration Ecosystem (Switching Cost)](#phase-5--integration-ecosystem)
8. [Phase 6 — Intelligence Upgrades (The Moat)](#phase-6--intelligence-upgrades)
9. [Phase 7 — Enterprise & Scale](#phase-7--enterprise--scale)
10. [Architecture Evolution](#architecture-evolution)
11. [Feature Priority Matrix](#feature-priority-matrix)
12. [Success Metrics](#success-metrics)

---

## 1. Current State Honest Assessment

### What's Built and Working
- RAG pipeline: ingest PDF/CHM → chunk → embed → vector search → LLM answer
- Two-service architecture (ingestor port 8081, bot port 8082)
- Product/version-scoped document retrieval (unique differentiator)
- JWT auth with ADMIN/USER roles
- Chat sessions with async summarization at 15 messages
- React frontend: chat area, sidebar, admin panel

### Strengths Worth Preserving
- The **product + version dual-filter** in vector search is a feature most RAG demos skip entirely — this is the core of the value proposition for teams managing multiple product releases
- The **shared PostgreSQL + pgvector** approach keeps the architecture simple without sacrificing power
- Clean domain-adapter-application layering means adding features won't create spaghetti

### Production-Breaking Issues (Blocking All Growth)

| Issue | Risk | Fix |
|-------|------|-----|
| `jpa.hibernate.ddl-auto: update` in production | Schema drift, silent data loss on restart | Flyway migrations |
| No pgvector index on `document_chunks.embedding` | O(n) sequential scan at >50k chunks — queries become 10+ seconds | HNSW index |
| CORS `allowedOrigins("*")` | Security gap | Config-driven whitelist |
| Frontend hardcodes `localhost:8081/8082` | Breaks in every non-local deployment | VITE env vars |
| No circuit breaker on LLM calls | One OpenAI outage takes down the entire service | Resilience4j |
| No rate limiting | Single user exhausts monthly LLM quota in minutes | Bucket4j per-user limits |
| JWT secret in `application.yml` | Secret cannot be rotated without redeployment | Env var only |
| Stack traces returned to clients on errors | Information leakage, unprofessional | `@RestControllerAdvice` |
| No health/readiness endpoints | Cannot deploy to Kubernetes, AWS ECS, or any container platform | Spring Actuator |
| No OpenAPI documentation | External developers and integrations cannot onboard | SpringDoc |

---

## Phase 0 — Production Foundation
### *"You cannot make something sticky if it breaks."*
**Timeline: Weeks 1–3**

---

### 0.1 Database Migrations with Flyway
Replace `ddl-auto: update` with versioned Flyway SQL migrations. Schema changes become auditable, reproducible, and reversible.

**Files to create:**
```
src/main/resources/db/migration/
  V1__initial_schema.sql          — current tables as-is
  V2__add_pgvector_index.sql      — HNSW index on embeddings
  V3__add_feedback_table.sql      — for Phase 1 answer feedback
  V4__add_bookmarks_table.sql     — for Phase 2 bookmarks
  V5__add_api_keys_table.sql      — for Phase 5 API access
```

**Why it matters**: Without this, every restart risks dropping a column. Teams won't trust a system that corrupts their data.

---

### 0.2 pgvector HNSW Index
```sql
CREATE INDEX ON document_chunks
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);
```
IVFFlat alternative for larger datasets (>1M chunks):
```sql
CREATE INDEX ON document_chunks
USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 200);
```

**Why it matters**: Without this, every query is a full table scan. At 100k chunks (roughly 500 documents), answer latency degrades from 200ms to 15+ seconds. This is Phase 0 because nothing else matters if the system is slow.

---

### 0.3 Resilience: Circuit Breakers + Retry + Bulkhead
Add Resilience4j to both services for all LLM calls:
- **Circuit breaker**: Open after 5 consecutive LLM failures — serve cached/degraded answers instead of hanging
- **Retry**: 3 attempts with exponential backoff (1s, 2s, 4s) for transient API errors
- **Bulkhead**: Max 5 concurrent LLM calls per service instance — prevents thundering herd
- **Fallback**: Return `"AI service is temporarily unavailable. Please try again in a moment."` — never a 500 error

---

### 0.4 Observability Stack
- **Micrometer + Prometheus**: `query_latency_seconds`, `llm_call_duration_seconds`, `chunks_retrieved_count`, `ingestion_failures_total`
- **Spring Boot Actuator**: `/actuator/health` (liveness), `/actuator/health/readiness` (readiness for K8s), `/actuator/metrics`
- **Structured logging**: Replace plain-text Slf4j with Logback JSON encoder — each log line has `traceId`, `spanId`, `userId`, `chatId`
- **Correlation IDs**: Micrometer Tracing propagates `X-Trace-Id` header across both services — critical for debugging cross-service issues

---

### 0.5 Global Error Handling
`@RestControllerAdvice` with typed exceptions and consistent response shape:
```json
{
  "code": "DOCUMENT_NOT_FOUND",
  "message": "Document with id 'abc' does not exist.",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```
No stack traces to clients. Every `404`, `400`, `403`, `409`, `503` returns the same shape. Frontend error handling becomes trivial.

---

### 0.6 Frontend Environment Configuration
Replace all `localhost:8081/8082` hardcodes with Vite env vars:
```
VITE_BOT_API_URL=https://api.yourdomain.com/bot
VITE_INGESTOR_API_URL=https://api.yourdomain.com/ingestor
```
One `.env.production` file controls the entire deployed frontend.

---

### 0.7 Rate Limiting
Bucket4j integration on `ChatController`:
- **Per user**: 30 queries/minute, 500/day (configurable)
- **Per IP (unauthenticated)**: 10 requests/minute
- **Response headers**: `X-RateLimit-Remaining`, `X-RateLimit-Reset`
- **429 response** with retry-after time, not a 500

---

### 0.8 OpenAPI / Swagger UI
SpringDoc OpenAPI on both services at `/swagger-ui.html`. Every endpoint documented with request/response schema, auth requirements, and example payloads. This unblocks every future integration in Phase 5.

---

### 0.9 Docker Compose for Production
Current `docker-compose.yml` is dev-only. Add:
- `docker-compose.prod.yml` with proper env var injection
- Separate `.env.example` with all required variables documented
- `POSTGRES_PASSWORD`, `OPENAI_API_KEY`, `JWT_SECRET` never hardcoded
- Named Docker volumes for PostgreSQL data persistence
- Health checks for all services

---

## Phase 1 — Trust & Accuracy
### *"The first reason users abandon a documentation AI is wrong answers. Make every answer feel trustworthy before adding features."*
**Timeline: Weeks 4–6**

---

### 1.1 Answer Confidence Indicator
Every `ChatResponse` gains a `confidence` field computed from retrieval quality:

| Signal | Weight |
|--------|--------|
| Max cosine similarity of top chunk | 40% |
| Average similarity of top-3 chunks | 30% |
| All top chunks from the exact requested version | 20% |
| At least 3 corroborating chunks | 10% |

**Frontend**: Colored badge next to every assistant message:
- 🟢 **HIGH** (≥0.80) — answer is well-grounded
- 🟡 **MEDIUM** (0.60–0.79) — answer has some support, may be incomplete
- 🔴 **LOW** (<0.60) — limited documentation found, treat with caution

Users learn when to trust the AI and when to go read the docs themselves. This actually increases trust more than just claiming high accuracy.

---

### 1.2 Strict Source Grounding (Hallucination Prevention)
If the maximum similarity score of retrieved chunks is below a configurable threshold (`bot.min-similarity-threshold: 0.55`), the system must **not** generate a speculative answer.

Return instead:
> "I couldn't find reliable documentation about this specific topic in [product] [version]. The closest match I found was about '[chunk topic]'. Could you rephrase, or are you looking for something in a different product or version?"

This is the single most important trust feature. Users who get one hallucinated answer lose faith permanently.

---

### 1.3 Precise Source Citations with Excerpts
Current implementation returns raw chunk content as "sources". Production citations need:
```json
{
  "sources": [
    {
      "documentName": "Case360_Installation_Guide",
      "product": "case360",
      "version": "23.4",
      "chunkIndex": 47,
      "pageNumber": 12,
      "relevanceScore": 0.891,
      "excerpt": "To install Case360 on Windows Server 2022, first ensure .NET 6.0 runtime is installed..."
    }
  ]
}
```
**Frontend**: Source pills below each answer — click to expand the exact excerpt that informed the answer. Hover to preview. This is how users verify the AI didn't fabricate.

---

### 1.4 Answer Feedback System
Thumbs up / thumbs down / "Report incorrect" on every assistant message.

**New table `answer_feedback`**:
```sql
CREATE TABLE answer_feedback (
  id UUID PRIMARY KEY,
  chat_message_id UUID REFERENCES chat_messages(id),
  user_id UUID REFERENCES users(id),
  rating SMALLINT NOT NULL,  -- 1 = helpful, -1 = not helpful
  feedback_text TEXT,
  created_at TIMESTAMP
);
```

This data feeds:
1. Admin analytics dashboard (Phase 4)
2. Automatic ranking improvement (down-ranked poor answers appear lower in search)
3. Documentation gap detection (Phase 6)

---

### 1.5 Version Conflict Surface
When retrieved chunks come from multiple versions matching the same query, the answer should note:
> "Note: This behavior differs between v12 and v14. The answer above reflects your selected version (v14). The v12 behavior was: [brief diff]."

Prevents users from acting on outdated information when multiple versions are ingested.

---

### 1.6 Intelligent Empty-State Response
Instead of: "I don't have information about that."

Return:
> "No documentation found for '[query]' in Case360 v23.4. The most relevant topic I have is '[nearest cluster topic]'. You might also try:
> - Searching all versions of this product
> - Asking about a related feature: [2 suggestions from query embeddings]
> - Browsing the available document topics for this product"

Empty states are where users give up. Make them a discovery moment instead.

---

## Phase 2 — Individual Stickiness
### *"Features that make individual users come back every day and feel the product knows them."*
**Timeline: Weeks 7–10**

---

### 2.1 Chat Session Auto-Titling
Currently sessions are titled by timestamp. After the first assistant response, generate a 5–7 word title using the LLM:
> "Installing Case360 on Windows Server"
> "Configuring LDAP Authentication v14"
> "Troubleshooting Database Connection Timeout"

Sidebar becomes a meaningful navigation history, not a list of `Chat 1, Chat 2, Chat 3`.

---

### 2.2 Bookmarks & Personal Answer Library
Users can bookmark any assistant message with an optional note and tags.

**New table `bookmarks`**:
```sql
CREATE TABLE bookmarks (
  id UUID PRIMARY KEY,
  user_id UUID REFERENCES users(id),
  chat_message_id UUID REFERENCES chat_messages(id),
  title VARCHAR(200),
  note TEXT,
  tags TEXT[],
  created_at TIMESTAMP
);
```

**Bookmarks page**: Search bookmarks full-text, filter by tag, sort by date. Export bookmark collection to Markdown. Re-run a bookmarked question against the current docs (detect if the answer changed since you saved it).

**Why this is sticky**: Once a user has 20 curated bookmarks organized by feature area, the cost of leaving is their entire personal knowledge base.

---

### 2.3 Export Conversations
Export any chat session as:
- **Markdown**: Clean prose with source footnotes — paste into wikis, tickets, PRs
- **PDF**: Formatted document with product/version header, timestamps, sources list
- **JSON**: Structured export for tool integrations

Button in chat header. Common use case: developer finds the answer, exports the conversation as documentation for their ticket.

---

### 2.4 Semantic Search Mode
Beyond conversational chat, add a dedicated **Search** mode:
- Single query box — not a conversation
- Returns ranked list of documentation excerpts with relevance scores
- Each result shows: document name, version, page, excerpt, similarity %
- Click any result to open a pre-seeded chat with that chunk as context
- Toggle between Chat mode and Search mode in the UI

Use case: "I don't need to have a conversation, I just need to find the section about X quickly." Serves power users who know what they're looking for.

---

### 2.5 Related Questions ("People Also Ask")
After each answer, surface 3 follow-up questions:

**Phase 2 implementation**: LLM generates them from the answer context:
> - "What are the system requirements for Case360 v23.4?"
> - "How do I upgrade from v22 to v23.4?"
> - "What changed in the authentication module in v23.4?"

**Phase 6 upgrade**: Replace with actual query log clustering — show questions real users asked after asking this same question. Much more valuable.

One-click to ask any of them immediately. Dramatically increases session depth (messages per chat).

---

### 2.6 Answer Regeneration
"Not satisfied?" button on any assistant message:
- **Regenerate**: Same question, different LLM seed
- **Make it more concise**: Add system instruction before regenerating
- **Add a code example**: Regenerate with explicit instruction
- **Regeneration history**: Arrow left/right to switch between versions

Users hate being stuck with a single bad answer. This keeps them in the product instead of going to Google.

---

### 2.7 Session Tags & Pinning
- Pin important sessions to top of sidebar (survives re-sorts)
- Tag sessions manually: `installation`, `api`, `troubleshooting`, `onboarding`
- Filter sidebar by tag
- Rename sessions inline

Once a user has organized their research history, it becomes a personal documentation log they won't want to rebuild elsewhere.

---

### 2.8 User Preferences
Per-user settings persisted server-side (not just localStorage):
- **Answer verbosity**: Concise (2–3 sentences) / Balanced (default) / Detailed (full explanation + examples)
- **Answer format**: Prose / Bullet Points / Code-First (lead with code, explain after)
- **Default product/version**: Pre-fill the product selector on new chats
- **Theme**: Light / Dark / System

These preferences are injected into every system prompt, personalizing every answer. A "code-first developer" and a "manager who needs the big picture" get meaningfully different answers to the same question.

---

### 2.9 Query History & Re-ask
- Search your own query history (not just sessions — individual questions)
- One-click re-ask any past question in a new session
- "Has the answer to this changed?" — re-run an old question and diff the new answer against the saved one

---

## Phase 3 — Team Collaboration
### *"Features that make the product stickier the more people at your company use it. Network effects."*
**Timeline: Weeks 11–14**

---

### 3.1 Shared Chat Links
Generate a shareable URL for any chat session: `/share/{shareToken}`

- **Team-only**: Requires authentication (default)
- **Public link**: Admin-configurable option
- Share page: read-only view with all messages and source citations
- **"Continue this conversation"** button — forks the shared chat into the viewer's own account
- Expiration: 7 days / 30 days / permanent

Use case: Developer finds the answer, shares the link in a Jira ticket or Slack thread. Teammates who click it arrive in Docs-inator with context already loaded. First conversion vector for new users.

---

### 3.2 Team Collections (Curated Knowledge Packs)
Team leads create named Collections:
> **"Case360 Onboarding"** · 12 items · Used by 8 people this week
> **"Common Installation Errors"** · 7 items · Last updated 3 days ago
> **"API Integration Guide"** · 23 items · ⭐ Pinned by admin

- Add any chat answer to a collection
- Collections are visible to the whole team (or role-restricted)
- Curated answers can be promoted to "Verified" by admins
- Collections survive individual employee departures — institutional knowledge that doesn't walk out the door

**Why this is the stickiest feature in the entire plan**: Once a team has 10 curated collections built up over months, the cost of migration is rebuilding all that institutional curation from scratch.

---

### 3.3 Answer Upvoting & Community Verification
Team members can upvote any answer (not just the AI that generated it — the answer itself):
- Answers with 3+ upvotes get a **"Team Verified"** badge
- Upvoted answers surface higher in future similar queries (reranking layer)
- Admins and designated doc owners can mark answers as **"Officially Approved"** (gold badge)
- Flagged answers (downvote + report) are queued for human review

Over time: Most verified answers are cached and served directly, bypassing the LLM call entirely for common questions. Faster AND cheaper.

---

### 3.4 Expert Escalation Workflow
When the AI returns LOW confidence AND user selects "Ask an Expert":

1. Question + AI's best attempt + relevant chunks are packaged
2. Notification sent to designated product experts (configurable per product)
3. Expert reviews in a dedicated **Escalations** tab in the admin panel
4. Expert types their answer — this answer is saved and linked to the original query pattern
5. Next time a semantically similar question is asked: expert's answer surfaces as a "Human Expert Answer" source

**Why this matters**: Documentation AI products are seen as unreliable for edge cases. Expert escalation turns the reliability problem into a feature — unknown unknowns get answered and permanently improve the system.

---

### 3.5 Inline Chunk Annotations
After an answer, any user can annotate the **source chunk** it cited:
> "[chunk excerpt]" — @priya: "This is outdated as of 23.5 — the `timeout` parameter was renamed to `connectionTimeout`. See migration guide."

Annotations are visible to all users whenever that chunk is shown as a source. Creates a living layer of institutional commentary on top of official documentation. The value compounds — every annotation makes the product smarter for everyone.

---

### 3.6 Notification Center
In-app notification feed:
- "A team member shared a chat with you: [title]"
- "Your bookmarked question has a new answer (docs updated)"
- "Collection '[name]' was updated with 3 new items"
- "Your escalated question was answered by [expert]"
- "[Product] v25.1 documentation has been ingested"

Pull model only in Phase 3. Phase 6 adds email/Slack digest.

---

## Phase 4 — Admin & Analytics Intelligence
### *"Once admins can see what their team needs, they will never operate without this data."*
**Timeline: Weeks 15–18**

---

### 4.1 Usage Analytics Dashboard
The admin home page becomes a command center:

**Overview tab**:
- Queries/day chart (last 30 days)
- Active users (DAU/WAU/MAU)
- Average session length (messages per chat)
- Answer quality distribution (% thumbs up vs down vs no rating)
- Top 10 most asked questions this week (clustered by semantic similarity)

**Product Coverage tab**:
- Per product/version: query volume, avg confidence, % LOW confidence queries
- Which products are under-documented based on query failure rate

**User Engagement tab**:
- User table: queries sent, avg feedback score, last active, sessions created
- New user onboarding funnel: registered → first query → second session → weekly active

---

### 4.2 Documentation Coverage Intelligence
**The killer feature for documentation teams.**

- **Coverage Heatmap**: Each document shown as a heatmap — sections cited frequently are hot, sections never cited are cold. Shows exactly where users are going and where they aren't.
- **Gap Detector**: Every LOW-confidence query is grouped by semantic topic. Surfaces: "Users asked about 'LDAP certificate renewal' 34 times this month. You have no documentation covering this."
- **Stale Content Detector**: Documents ingested >6 months ago that are still queried but always receive poor feedback — flagged for documentation team review.
- **Coverage Score per Product**: Aggregate metric — "Case360 v23 has 84% coverage of common user queries."

Export gap report as CSV → technical writers know exactly what to write next. This turns the admin from a system manager into a strategic documentation prioritizer.

---

### 4.3 Query Intelligence
Deep analysis of the query log:
- **Query Clustering**: Group all queries by semantic similarity — reveals the most common user intents
- **Failed Query Analysis**: All queries that got LOW confidence or negative feedback — prioritized by frequency
- **Query Seasonality**: Are "installation" queries spiking after each release? Are "upgrade" questions peaking during certain months?
- **Answer Quality Trend**: Is the system getting better or worse over time as docs change?

---

### 4.4 Cost Tracking
Per query, log:
- Embedding tokens used (query + context)
- Chat completion tokens in + tokens out
- Estimated cost (configurable per-model pricing table)

Admin view:
- Total LLM cost per day/month
- Cost per user (identify heavy users)
- Cost per product (identify expensive products)
- Budget alerts: "80% of monthly budget consumed"
- Model recommendation: "42% of queries are simple — using gpt-4o-mini for those would save $X/month"

---

### 4.5 Document Lifecycle Management
Beyond the current status tracking:
- **Document tagging**: Mark documents as `stable`, `draft`, `deprecated`
- **Scheduled re-ingestion**: Cron-based automatic re-ingestion for documents hosted at a URL
- **Bulk operations**: Delete all chunks for a version, rename a product across all documents
- **Ingestion health dashboard**: Queue depth, avg processing time, failure rate trend, last 10 errors with full context
- **Document comparison**: When re-ingesting a document, show a diff of changed chunks — which sections were added/modified/removed
- **Rollback**: Keep previous version of chunks for 30 days — ability to roll back to previous ingestion if new version degrades answer quality

---

### 4.6 Product/Version Access Control (RBAC+)
Beyond the binary ADMIN/USER role:

**New table `user_product_access`**:
```sql
CREATE TABLE user_product_access (
  user_id UUID REFERENCES users(id),
  product VARCHAR(100),
  version VARCHAR(50),   -- NULL = all versions
  PRIMARY KEY (user_id, product, version)
);
```

- Users can only query products they have access to
- ADMIN assigns access by user or by team group
- Public products: accessible to all authenticated users (configurable default)
- Restricted products: require explicit grant
- Version-level restriction: beta/early-access docs visible only to specific users

---

### 4.7 Full Audit Log
Immutable log of every significant action:
```sql
CREATE TABLE audit_log (
  id UUID PRIMARY KEY,
  actor_id UUID,
  action VARCHAR(100),     -- QUERY, UPLOAD, DELETE, LOGIN, EXPORT, etc.
  target_type VARCHAR(50), -- DOCUMENT, CHAT, USER, COLLECTION
  target_id UUID,
  metadata JSONB,          -- query text, document name, etc.
  ip_address VARCHAR(45),
  created_at TIMESTAMP
);
```
Admin can filter, search, and export. Required for SOC2 / GDPR / HIPAA compliance (Phase 7), but build the structure now.

---

## Phase 5 — Integration Ecosystem
### *"Embed the product inside every workflow. Make the switching cost real."*
**Timeline: Weeks 19–24**

---

### 5.1 REST API with API Keys
Programmatic access to the query engine:

```
POST /api/v1/query
Authorization: ApiKey sk-docai-xxxxxxxxxxxx
{
  "question": "How do I configure LDAP?",
  "product": "case360",
  "version": "23.4"
}
```

**New table `api_keys`**:
```sql
CREATE TABLE api_keys (
  id UUID PRIMARY KEY,
  user_id UUID REFERENCES users(id),
  key_hash VARCHAR(64),    -- never store plaintext
  name VARCHAR(100),
  scopes TEXT[],           -- ['query', 'upload', 'admin']
  rate_limit_per_min INT,
  last_used_at TIMESTAMP,
  expires_at TIMESTAMP,
  created_at TIMESTAMP
);
```

Features:
- Create/revoke keys from user settings
- Per-key usage tracking
- Per-key rate limiting
- Scoped permissions: query-only vs. upload vs. full admin
- Usage shown in admin dashboard

Once teams can call the API from scripts, CI/CD pipelines, and internal tools, the product is embedded in their infrastructure.

---

### 5.2 Slack Bot Integration
Single highest-ROI integration. Target: developers who live in Slack.

**Capabilities**:
- `/docs ask [question]` — answer posted in channel
- `@docs-inator [question]` — mention in any thread
- Direct messages for private queries
- Answer shows confidence badge + source links
- `/docs set-product case360 23.4` — configure default product per channel
- Answer thread includes: "Continue this conversation →" link to full chat in Docs-inator

**Implementation**: Slack Bolt SDK (Node.js service or Java Slack SDK) — thin adapter that calls the API from 5.1. Does not require any changes to the core services.

**Why this wins**: 90% of developer questions are asked in Slack. If the bot answers correctly in-channel, the entire team sees the answer and learns where to get future answers. Viral growth within organizations.

---

### 5.3 Webhook-Based Document Ingestion
Push documents to Docs-inator from CI/CD pipelines:

```
POST /api/v1/ingest/webhook
ApiKey: sk-docai-xxxxxxxxxxxx
{
  "downloadUrl": "https://your-cdn.com/docs/case360-23.5-install-guide.pdf",
  "product": "case360",
  "version": "23.5",
  "documentName": "Installation Guide"
}
```

Service downloads, hashes, and ingests. Returns a job ID for status polling.

Use case: Build system publishes new docs → triggers webhook → Docs-inator auto-ingests → users are querying new version docs within minutes of release. Zero manual admin effort.

---

### 5.4 Microsoft Teams Integration
Same architecture as Slack bot but for Teams-first organizations. Teams bot framework + the same API from 5.1. Reaches a completely different enterprise buyer.

---

### 5.5 Chrome / Edge Browser Extension
- Highlights selected text on any webpage → "Ask Docs-inator about this" in context menu
- Extension popup: mini chat interface
- Configures default product/version per domain (e.g., "when I'm on jira.company.com, default to case360 23.4")
- No context switching — stay on the page you're debugging

Use case: Developer reading a stack trace in Jira, selects the error code, asks Docs-inator without leaving the tab.

---

### 5.6 VS Code Extension (Developer Companion)
- Right-click on any symbol/method in code → "Search documentation for this"
- Inline documentation panel in editor sidebar
- Workspace setting: `.vscode/docs-inator.json` with `{ "product": "case360", "version": "23.4" }`
- Answers appear in VS Code panel with source links

Once developers have this installed, they never go to a documentation portal again. The product becomes part of their IDE muscle memory.

---

### 5.7 Confluence / Notion Connector
Pull documents directly from content management systems:
- **Confluence**: Connect via API token → select spaces → auto-ingest all pages
- **Notion**: Connect via integration → select databases → sync pages
- Scheduled sync: re-ingest modified pages automatically
- Bidirectional (Phase 6): Push curated Q&A answers back to Confluence as pages

Removes the manual upload step entirely for teams already writing docs in Confluence/Notion.

---

### 5.8 Email Digest
Weekly or daily email summary per user:
- "This week's most-asked questions about your products"
- "3 new documents ingested for your subscribed products"
- "You have 2 bookmarks where the underlying documentation has changed"

Brings users back to the product even when they haven't had a need that week.

---

## Phase 6 — Intelligence Upgrades
### *"The moat. Features that require months of data accumulation and cannot be copied."*
**Timeline: Weeks 25–32**

---

### 6.1 Multi-Hop Reasoning (Query Decomposition)
Current pipeline: one retrieval pass → one generation.

Complex questions require multiple retrieval passes:
> "How does the authentication module interact with session management when SSO is enabled in Case360?"

**Enhanced pipeline**:
1. Decompose the question into 2–3 sub-questions using LLM
2. Run vector search independently for each sub-question
3. Synthesize results from all retrievals into a final answer that addresses the whole question

Answers questions that span multiple documentation sections — the class of questions documentation AIs usually fail at.

---

### 6.2 Version Diff Intelligence
> "What changed in the configuration API between Case360 v22 and v23.4?"

**Implementation**:
1. Retrieve top-K chunks for the query from version A
2. Retrieve top-K chunks for the query from version B
3. LLM compares chunks and generates structured changelog:
   - **Added**: New parameters, methods, features
   - **Modified**: Changed behavior, renamed options
   - **Removed**: Deprecated or deleted functionality
   - **Breaking changes**: Highlighted prominently

Use case: Developer upgrading between versions. This is the most common and highest-stakes query category for versioned product documentation — and almost no RAG system handles it well.

---

### 6.3 Auto-FAQ Generator
Weekly scheduled job:
1. Cluster all queries from last 30 days by semantic similarity (k-means on query embeddings)
2. For each cluster with ≥5 queries: identify the canonical question form
3. Retrieve the best answer for that canonical question
4. Generate a FAQ entry: question + answer + sources

Admin review queue: approve, edit, or reject each FAQ entry. Approved FAQs become a Collection visible to all users as "Frequently Asked Questions."

**Why this competes with expensive enterprise knowledge bases**: It writes its own FAQ from actual user behavior, not what doc writers think people ask.

---

### 6.4 Documentation Gap Report (AI-Driven)
Monthly report generated automatically:
- All LOW-confidence queries grouped by semantic topic cluster
- Each cluster: how many queries, how many unique users, example questions
- For each cluster: LLM generates a one-paragraph documentation stub that would answer it
- Exported as Markdown → technical writers copy it into their documentation system

Transforms the admin from system operator into proactive documentation strategist. Shows concrete ROI to documentation team leadership.

---

### 6.5 Answer Evolution Timeline
For any question: "How has the answer to this changed across product versions?"

Show a timeline: v10 → v11 → v12 → v13 → v14, with the answer to the current question at each version. Changes highlighted. Breaking changes marked in red.

Use case: Support engineer debugging a customer issue on an older version. Instead of asking "what does this setting do in v11?", they can see the full history and understand exactly when behavior changed.

---

### 6.6 Proactive Topic Subscriptions
Users subscribe to topics or products:
> "Notify me when documentation for Case360 authentication is updated"

When new documents are ingested or chunks change significantly:
1. Detect which subscribed topics are affected (semantic similarity to changed chunks)
2. Notify subscribed users: "Case360 v23.5 authentication documentation has been updated. 3 sections changed that match your interests."
3. Link directly to a pre-seeded chat comparing the old and new behavior

Brings users back to the product on their subscribed topics without them having to check manually.

---

### 6.7 Semantic Chunking v2
Current: fixed-size token chunking (800 tokens, 100 overlap).

Improved chunking strategy that dramatically improves retrieval quality:
1. **Semantic boundary detection**: Split at natural boundaries — paragraph breaks, headers, list terminations — not arbitrary token counts
2. **Hierarchical chunk storage**: Store both the leaf paragraph (small, precise) and its parent section (large, contextual)
3. **Small-to-big retrieval**: Search against small chunks (precise match), return parent context (full explanation)
4. **Chunk metadata enrichment**: Extract and store section headers, table-of-contents path, page number during chunking

Same documents, noticeably better answers. No retraining needed.

---

### 6.8 Contextual Code Generation
For technical documentation with code examples:
1. During ingestion: detect and separately index code blocks with their surrounding explanation
2. During answer generation: if the question is "how do I do X in code", retrieve the code-specific index first
3. Generate complete, working code in the user's preferred language (from user preferences in 2.8)
4. Code is grounded in the documentation — never generated from model training data alone

User preference from Phase 2 (`format: Code-First`) triggers this path automatically.

---

### 6.9 "People Also Asked" from Real Query Data
Replace the LLM-generated follow-up questions from Phase 2 with actual query log data:

After answering a question, show: "Other users who asked this also asked:"
- Powered by query session graph: queries made in the same session, within 10 minutes of this query
- Weighted by frequency and recency
- Highly personalized to the product/version context

Requires ~3 months of query accumulation to be meaningful. The longer you run it, the better it gets. Impossible for a competitor to replicate without your data.

---

## Phase 7 — Enterprise & Scale
### *"Features that close enterprise procurement. Pays for everything that came before."*
**Timeline: Weeks 33–40**

---

### 7.1 Multi-Tenancy
Full tenant isolation for SaaS deployment:
- Schema-per-tenant PostgreSQL (strongest isolation) or row-level security
- Tenant admin manages their own users, documents, products, settings
- Cross-tenant data isolation guaranteed at database level
- Tenant-specific LLM API key configuration
- Per-tenant branding (logo, color scheme, product name)

---

### 7.2 SSO / OIDC / SAML Integration
Enterprise authentication:
- **OIDC**: Azure AD, Google Workspace, Okta, Auth0
- **SAML 2.0**: Legacy enterprise IdPs
- **Group mapping**: AD groups → Docs-inator roles/product access
- **JIT provisioning**: Account created on first login, no pre-provisioning needed
- **SCIM**: Automated user provisioning/deprovisioning from HR systems

Removes the #1 blocker for enterprise procurement: "We can't use tools that don't integrate with our IdP."

---

### 7.3 Compliance Package (SOC2 / GDPR / HIPAA-Ready)
- Immutable audit log from Phase 4 is the foundation
- **GDPR**: User data export (all queries, bookmarks, annotations) + right to be forgotten (cascade delete)
- **Data retention policies**: Configurable auto-deletion of query logs after N days
- **Data residency**: EU and US deployment regions, no cross-region data transfer
- **Encryption at rest**: Document content and embeddings encrypted in PostgreSQL
- **PII detection**: During ingestion, flag documents that may contain PII (names, emails, SSNs) — alert admin before indexing

---

### 7.4 Multi-LLM Routing
Decouple from OpenAI completely:
- **LLM abstraction layer**: `LLMProvider` interface with implementations for OpenAI, Azure OpenAI, Anthropic Claude, Google Gemini, Ollama (local)
- **Per-tenant provider configuration**: Organization A uses Azure OpenAI (data stays in their Azure tenant), Organization B uses Claude
- **Intelligent routing**: Simple factual queries → cheap fast model; complex multi-hop → powerful expensive model
- **Fallback chain**: Primary LLM fails → secondary LLM → cached degraded response

Removes vendor lock-in — a major enterprise procurement objection.

---

### 7.5 Horizontal Scaling Infrastructure
- **Redis**: Session cache, rate limit counters, embedding cache (frequently queried embeddings cached 1 hour)
- **S3 / MinIO**: Document and chunk storage — not local filesystem
- **Message queue (RabbitMQ/SQS)**: Ingestion requests queued and processed by worker pool — handles bursts of document uploads without blocking
- **PgBouncer**: Connection pooling between services and PostgreSQL
- **Kubernetes**: Helm charts, horizontal pod autoscaler, liveness/readiness probes, resource limits

---

### 7.6 White-Label / Custom Branding
- Custom logo, color scheme, product name via admin settings
- Custom domain: `docs.yourcompany.com` with your SSL cert
- Custom email templates (for notifications, escalations)
- Remove all "Docs-inator" references for OEM resale

---

---

## Architecture Evolution

### Current State
```
[React SPA] ────────────────────────────────────────────────────
                        │                          │
               [Bot Service :8082]     [Ingestor Service :8081]
                        │                          │
               [PostgreSQL + pgvector]─────────────┘
```

### Phase 0–2 Target
```
[React SPA]
    │
[Bot :8082] + [Ingestor :8081]
    │                │
[PostgreSQL+pgvector] [Local FS]
    │
[Prometheus/Grafana]
```

### Phase 3–5 Target
```
[React SPA] + [Slack Bot] + [Chrome Ext] + [VS Code Ext]
                    │
            [API Gateway / Nginx]
           /          |          \
  [Bot :8082]  [Ingestor :8081]  [Notification Service]
       |              |
  [PostgreSQL+pgvector]  [Redis]  [S3 / MinIO]
                    |
           [Prometheus / Grafana / Loki]
```

### Phase 7 Target
```
[React SPA] + [Slack] + [Teams] + [Chrome] + [VSCode] + [Confluence]
                            │
                   [API Gateway + Auth]
              /        |        |        \
    [Bot Cluster]  [Ingestor] [Analytics] [Notification]
         |              |
  [PgBouncer]→[PostgreSQL+pgvector (HA)]
         |
  [Redis Cluster]  [S3]  [RabbitMQ]
         |
  [Grafana Stack]  [Audit Log (immutable)]
```

---

## Feature Priority Matrix

| Feature | Stickiness | Effort | Phase | Priority |
|---------|-----------|--------|-------|----------|
| Flyway migrations | Foundation | XS | 0 | P0 — BLOCKER |
| pgvector HNSW index | Performance | XS | 0 | P0 — BLOCKER |
| Circuit breakers + retry | Reliability | S | 0 | P0 — BLOCKER |
| Frontend env vars | Deployability | XS | 0 | P0 — BLOCKER |
| Global error handler | Trust | S | 0 | P0 — BLOCKER |
| Answer confidence scoring | Trust | M | 1 | P0 |
| Source grounding / hallucination guard | Trust | S | 1 | P0 |
| Answer feedback (thumbs up/down) | Data flywheel | S | 1 | P0 |
| Precise source citations | Trust | S | 1 | P1 |
| Session auto-titling | UX | S | 2 | P1 |
| Bookmarks & answer library | **HIGH** | M | 2 | P1 |
| Export conversations | Workflow | S | 2 | P1 |
| Semantic search mode | Discovery | M | 2 | P1 |
| Query analytics dashboard | Admin lock-in | M | 4 | P1 |
| Answer regeneration | Satisfaction | S | 2 | P1 |
| User preferences (verbosity/format) | Personalization | M | 2 | P1 |
| Shared chat links | Viral growth | S | 3 | P2 |
| Team collections | **HIGHEST** | M | 3 | P2 |
| Answer upvoting & community verify | Network effects | M | 3 | P2 |
| Expert escalation | Reliability | L | 3 | P2 |
| Coverage gap detector | Admin value | L | 4 | P2 |
| Cost tracking | Admin lock-in | M | 4 | P2 |
| REST API with API keys | Ecosystem | M | 5 | P2 |
| Slack bot | **HIGHEST** | L | 5 | P2 |
| Webhook ingestion | Admin automation | M | 5 | P2 |
| Version diff intelligence | Unique value | L | 6 | P3 |
| Multi-hop reasoning | Answer quality | L | 6 | P3 |
| Auto-FAQ generator | Automation | L | 6 | P3 |
| Documentation gap report | Strategic value | L | 6 | P3 |
| "People also asked" from query data | Compounding value | M | 6 | P3 |
| VS Code extension | Developer adoption | L | 5 | P3 |
| SSO/OIDC | Enterprise sales | L | 7 | P4 |
| Multi-tenancy | SaaS revenue | XL | 7 | P4 |
| Multi-LLM routing | Vendor independence | L | 7 | P4 |
| Compliance package | Enterprise sales | L | 7 | P4 |

*Effort: XS=days, S=1 week, M=2–3 weeks, L=4–6 weeks, XL=2+ months*

---

## Success Metrics

| Metric | Current | Phase 2 Target | Phase 5 Target |
|--------|---------|----------------|----------------|
| Answer accuracy (% thumbs up) | Unknown | >80% | >90% |
| Query → first byte latency | ~3–5s | <2s | <1.5s |
| DAU / MAU ratio | Unknown | >35% | >55% |
| Avg messages per chat session | Unknown | >5 | >8 |
| 30-day user retention | Unknown | >45% | >65% |
| Queries with LOW confidence | Unknown | <15% | <8% |
| Documents ingested concurrently | Sequential | 4 parallel | Queue-backed |
| Supported concurrent users | ~5 | 100 | 1,000+ |
| LLM cost per query | Unknown | Tracked | Optimized (<$0.01 avg) |
| Documentation coverage score | Unknown | Measured | >80% per product |

---

## Technical Debt Register

These must be resolved before the product goes to paying customers. They accumulate interest — every week without fixing them costs more.

| Debt Item | Risk Level | Fix | Phase |
|-----------|-----------|-----|-------|
| `ddl-auto: update` in production | CRITICAL | Flyway | 0 |
| No pgvector index | HIGH | HNSW index | 0 |
| No circuit breakers on LLM | HIGH | Resilience4j | 0 |
| Frontend hardcodes localhost | HIGH | VITE env vars | 0 |
| CORS `allowedOrigins("*")` | MEDIUM | Config whitelist | 0 |
| JWT secret in yml | MEDIUM | Env var only | 0 |
| Stack traces to clients | MEDIUM | @RestControllerAdvice | 0 |
| No rate limiting | MEDIUM | Bucket4j | 0 |
| Local file storage for documents | MEDIUM | S3/MinIO | 7 |
| No message queue for ingestion | LOW | RabbitMQ/SQS | 7 |
| Shared DB between two services | LOW | Acceptable now, split at scale | 7 |
| `@Async` without thread pool config | LOW | Configure ThreadPoolTaskExecutor | 0 |
| No integration tests | MEDIUM | TestContainers with real PostgreSQL | 0–1 |

---

*This roadmap was written against the codebase as of June 2026. Phases 0–2 are sequenced for maximum early value. Phases 3–7 can be partially parallelized with dedicated workstreams.*
