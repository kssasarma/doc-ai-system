# TODO-PROD.md — Production Roadmap v2

> The successor roadmap to `TODO.md` (which records 9 completed phases and is retained as an archive — do not edit it).
> This file is **self-contained**: any agent can pick up any single item without reading the archive or re-auditing the codebase. Every claim below was verified against the current source (grep/read), not assumed.

---

## System snapshot (what exists today)

- **`documentation-bot/`** — Spring Boot 4 / Java 21, port 8082. Chat + RAG answering, auth (JWT + refresh tokens + API keys + OIDC JIT), 30 REST controllers, multi-tenant row-level isolation (`TenantContext` ThreadLocal, fails closed), per-document + per-group ACL (`DocumentAccessPolicy` → `SearchScope`), hybrid dense+lexical retrieval with RRF fusion + MMR re-ranking, Flyway V1–V18.
- **`document-ingestor/`** — Spring Boot 4 / Java 21, port 8081. Upload → parse (Tika, structure-preserving) → chunk (`SemanticChunker`) → PII scan → embed (OpenAI) → pgvector. Storage is S3/MinIO only (no local disk). Flyway V1–V9.
- **`frontend/`** — React 18 + Vite + TS + Tailwind v3 + framer-motion + Headless UI. Design-system primitives in `src/components/ui/`, dark mode, command palette, toasts. Data fetching is raw `fetch`/axios + `useEffect` (no query library).
- **Shared Postgres 16 + pgvector**; MinIO in dev compose; Redis optional (bot only). Roles: `SUPER_ADMIN` (creates tenants only), `ADMIN` (tenant admin), `USER`. Invite-only provisioning; multi-tenant membership with active-workspace switching.
- Integrations: `slack-bot/`, `teams-bot/`, `vscode-extension/`, `browser-extension/` — all correctly use `POST /api/v1/query` with `X-API-Key`; tenant is resolved server-side from the key. Deploy: `docker-compose(.prod).yml`, `k8s/`, `helm/doc-ai-system/`, CI in `.gitlab-ci.yml`.

## The 8 product requirements this roadmap serves

1. Best-in-class, appealing frontend everyone loves to use.
2. Exceptionally solid backend; multitenancy without any issues.
3. ADMINs can configure LLMs **and API keys**.
4. SUPER_ADMIN only creates tenants. *(Already correctly enforced — `TenantController` CRUD is `SUPER_ADMIN`-only; per-tenant config is self-service. No work needed.)*
5. Each tenant admin manages their LLM models, API key, documents, and ACLs.
6. Fail-proof and consistent.
7. Cache invalidation on new document versions; product/version listings auto-update.
8. "Google for the company" — research/find anything in documents via NLP.

## How to use this file

- Status legend: `[ ]` not started, `[~]` in progress, `[x]` done.
- Item format: **Defect** (what's wrong) → **Evidence** (file:line as of this writing — re-verify before editing, lines drift) → **Fix** (approach, naming existing code to reuse) → **Accept** (how you know it's done).
- Each phase ends with a **Verify** block — run it before marking the phase complete.
- Phases are ordered by severity and dependency; the build-order section at the end notes cross-phase dependencies.
- Conventions to preserve: Flyway migrations `V{N}__desc.sql` per service (never edit applied ones; bot is at V18, ingestor at V9); no FK between bot-owned and ingestor-owned tables (independent migration sequences); `TenantContext.get()` fails closed; tests use Testcontainers with `ddl-auto: create-drop` (`PostgresTestContainerBase`); frontend build gate is `npx tsc --noEmit && npm run build`.

---

## Phase 1 — Per-tenant LLM config + encrypted BYO API keys  `[CRITICAL — requirements 3 & 5]`

**Goal:** Tenant admins configure provider/model **and their own API key** (encrypted at rest), and that configuration actually drives every LLM call. Policy (decided): **platform fallback** — use the tenant's key when configured, else the global env-var key; tenants work out of the box.

- [ ] **1.1 Wire `LLMRouter` into the real inference path — it is currently dead code.**
  - Defect: `LLMRouter` (`documentation-bot/src/main/java/com/docai/bot/application/service/LLMRouter.java`) is never injected by anything (verified: repo grep finds only self-references, `LLMProvider` javadoc, and a `TenantService` comment). Every real LLM call uses the global Spring AI `ChatClient.Builder` with OpenAI marked `@Primary` in `config/ChatModelConfig.java`. So `TenantLLMConfig.chatProvider/chatModel/routingEnabled/simpleModel/complexModel` are persisted and editable via `PUT /api/admin/tenants/{id}/llm-config` but have **zero runtime effect**.
  - Evidence: direct `chatClientBuilder` usage at `AnswerGenerationService.java:32,259` (+ `:124` title generation), `MultiHopReasoningService.java:219,264`, `ReRankingService.java:103`, `QueryAnalyzerService.java:79`, `ChatSummaryService.java:67`, `AnswerEvolutionService.java:129`, `VersionDiffService.java:135`, `DocumentationGapService.java:177`.
  - Fix: make `LLMRouter` the single entry point for chat completions. Give it a method like `ChatClient clientFor(UUID tenantId, boolean complexQuery)` that resolves `TenantLLMConfig` (cache per-tenant with invalidation on config update), picks provider+model (honoring `routingEnabled`/`simpleModel`/`complexModel`), and falls back to the platform OpenAI client on any provider failure (behavior it already implements — keep it). Replace all 8 direct `chatClientBuilder` call sites. Tenant id comes from `TenantContext.get()` at each site (all run on request threads except scheduled jobs — `AutoFaqService`/`DataRetentionService` loop per-tenant already and can pass the tenant explicitly).
  - Accept: setting a tenant's `chatModel` to a distinct model and asking a question produces a request to that model (assert via provider logs or a wiremock); a second tenant with no config still uses the platform default; provider failure falls back to OpenAI without a user-facing error.

- [ ] **1.2 Implement encrypted per-tenant API key storage (`apiKeyEnc` is a dead column today).**
  - Defect: `TenantLLMConfig.apiKeyEnc` (`domain/entity/TenantLLMConfig.java:51-52`, column added in `V12__phase7_compliance_branding_llm.sql:63` with a comment claiming "AES-256 encrypted") is never read anywhere, never written by `TenantService.updateLLMConfig` (`application/service/TenantService.java:124-135` copies every field **except** `apiKeyEnc`), and **no encryption code exists in the repo** (grep for `Cipher|AES|SecretKey` matches only JWT signing).
  - Fix: new `SecretsCryptoService` (AES-256-GCM, random 12-byte IV per encryption prefixed to ciphertext, key from env `SECRETS_ENCRYPTION_KEY` — base64, 32 bytes; fail startup if missing in prod profile, generate-and-warn in dev). Reuse it in Phase 4.4 for integration tokens. `updateLLMConfig` encrypts and persists an incoming key; `LLMRouter` decrypts at client-build time. Never log the plaintext.
  - Accept: DB row shows ciphertext (not the raw `sk-...`); round-trip unit test; restart with a wrong `SECRETS_ENCRYPTION_KEY` fails decryption gracefully (falls back to platform key + logs an alert, does not 500 the chat).

- [ ] **1.3 Stop exposing the raw `TenantLLMConfig` entity on the API; make the key write-only.**
  - Defect: `TenantController` (`adapter/rest/TenantController.java:87-98`) uses the JPA entity directly as `@RequestBody` and response body. Once 1.2 lands, `GET /llm-config` would leak the (encrypted, but still) key material, and mass-assignment of `id/tenantId` remains a smell.
  - Fix: introduce `LlmConfigRequest`/`LlmConfigResponse` record DTOs. Request has optional `apiKey` (plaintext in, encrypted at rest; `null` = keep existing; empty string = clear). Response never returns the key — return `hasCustomKey: boolean` and a masked hint (last 4 chars) instead.
  - Accept: `GET` response contains no key material; `PUT` without `apiKey` preserves the stored key; `PUT` with `"apiKey": ""` clears it.

- [ ] **1.4 Per-tenant embeddings (bot side).**
  - Defect: `VectorSearchService.java:49,158-167` calls the global `EmbeddingModel` bean; `TenantLLMConfig.embeddingProvider/embeddingModel` are ignored.
  - Fix: route query-embedding generation through `LLMRouter` (or a sibling `EmbeddingRouter`) honoring tenant config + key with platform fallback. **Constraint to enforce:** the query-time embedding model MUST match the model used at ingest time for that tenant's documents, or similarity scores are garbage — persist the embedding model name per document/chunk set (see 1.5) and refuse (log + fall back to the ingest-time model) if a tenant flips embedding config while old vectors exist. Simplest safe rule: embedding model changes only take effect for newly ingested documents, and search uses the model recorded on the documents in scope; document this in the Settings UI.
  - Accept: integration test proving a tenant with a custom embedding config still gets correct retrieval against documents embedded before the change.

- [ ] **1.5 Per-tenant embeddings + key (ingestor side).**
  - Defect: ingestor uses a single global `OPENAI_API_KEY` (`document-ingestor/src/main/resources/application.yml:36`) for all tenants' embeddings; it has no access to `TenantLLMConfig` (bot-owned table) and records no embedding-model name on documents.
  - Fix: (a) add `embedding_model` column to `documents` (ingestor migration V10) recorded at ingest time; (b) ingestor reads tenant LLM config — since both services share one Postgres, add a read-only repository over the `tenant_llm_configs` table (same pattern as the bot reading ingestor-owned `documents`); decrypt via the same `SecretsCryptoService` (share `SECRETS_ENCRYPTION_KEY` env across services, like `JWT_SECRET` already is); (c) build the OpenAI embedding client per tenant with platform fallback.
  - Accept: two tenants with different keys produce embedding API calls with different Authorization headers (wiremock); `documents.embedding_model` populated on new ingests.

- [ ] **1.6 Frontend: API-key entry + connection test in LLM settings.**
  - Defect: `frontend/src/components/Admin/pages/SettingsPage.tsx:133-190` (and the super-admin variant in `TenantsPage.tsx:258-292`) exposes provider/model/routing fields but **no API key field** — an admin cannot fulfill requirement 5 from the UI at all.
  - Fix: add a password-type key input (masked, with show/hide, "custom key configured •••• ab12" state from `hasCustomKey`, and an explicit "Remove custom key" action) + a **"Test connection"** button hitting a new `POST /api/admin/tenants/{id}/llm-config/test` (backend sends a 1-token completion with the pending config and returns ok/error message — do not persist on test). Reuse `Input`, `Button`, `useToast` primitives.
  - Accept: enter a bad key → test fails with a readable message and nothing saved; enter a good key → save → `hasCustomKey` renders; chat for that tenant uses the new key (1.1/1.2).

- [ ] **1.7 Audit-log LLM config changes** (currently silent — see Phase 4.9): every `updateLLMConfig` writes an `AuditLog` row (actor, tenant, changed fields, **never** key material).

**Verify (Phase 1):** With two tenants A (custom Anthropic key + model) and B (no config): chat in A hits Anthropic with A's key; chat in B hits platform OpenAI; kill A's key validity → A's chat falls back to platform OpenAI with a logged warning, no user-facing failure; `GET llm-config` leaks nothing; DB shows ciphertext; audit log shows the config change. `mvn test` green in both services; `npx tsc --noEmit && npm run build` clean.

---

## Phase 2 — Document versioning, supersession, ingestion integrity & invalidation  `[CRITICAL/HIGH — requirements 6 & 7]`

**Goal:** A new version of a document cleanly replaces same-version content, search never sees failed/partial/stale chunks, downstream consumers (subscriptions, frontend product lists) learn about new versions automatically, and nothing gets permanently stuck.

- [ ] **2.1 Same product+version re-upload must supersede, not duplicate.**
  - Defect: dedup is keyed on `fileHash`+`tenantId` only (`document-ingestor/.../adapter/rest/DocumentUploadController.java:96-135`); the `(product,version)` index is non-unique (`V1__initial_schema.sql:29`). Uploading edited bytes for an existing product+version creates a **second document whose old sibling's chunks remain fully searchable** — duplicate and contradictory content forever.
  - Fix: in the upload flow (and webhook/connector ingest paths), after a successful ingestion of tenant+product+version, atomically delete (or mark `SUPERSEDED` and exclude from search — deletion is simpler and consistent with the existing "chunks are derived data" model) all *other* `COMPLETED` documents with the same `(tenantId, product, version)` and their chunks, in one transaction. Also delete their stored objects via `DocumentStorageService.delete` and their `DocumentAccess`/`GroupDocumentAccess` grants are bot-owned — leave rows (they reference the old UUID and become inert) but add a cleanup note; better: emit the completion event (2.4) carrying old+new document ids so the bot can migrate grants from the superseded doc to its replacement (same product+version ⇒ same intended audience).
  - Accept: upload v2.1.0 of "Manual", then upload an edited v2.1.0 → exactly one `COMPLETED` document row for that (tenant, product, version); chat retrieval returns only new content; grants carry over; old S3 object gone.

- [ ] **2.2 Search must only see `COMPLETED` documents.**
  - Defect: the live retrieval queries `findTopNDenseAccessible`/`findTopNLexicalAccessible` (`documentation-bot/.../domain/repository/DocumentChunkRepository.java:108-136`) filter tenant + granted ids but **never `d.status`** (verified: zero `status` references in the file). Combined with 2.3's partial-commit behavior, users can get answers from half-ingested or failed documents.
  - Fix: add `AND d.status = 'COMPLETED'` to both native queries (and to `DocumentRepository.findDistinctProductVersionsAccessible` behind `/api/products`).
  - Accept: a document forced to `FAILED` with existing chunks is invisible to search and to `/api/products`; flipping it to `COMPLETED` makes it visible.

- [ ] **2.3 Make ingestion atomic per document — no committed orphan chunks.**
  - Defect: `IngestionService.ingestUploadedFile`/`processDocument` (`application/service/IngestionService.java:41-67,104-176`) is one `@Async @Transactional` method that catches embedding failures **inside** the transaction (`:59-66`), so chunks 0..N-1 commit alongside `status=FAILED`. Also the whole multi-minute embed loop holds one DB connection/transaction.
  - Fix: restructure — parse + chunk + embed **outside** any transaction (embeddings are pure API calls); then a short `@Transactional` write that inserts all chunks + flips status to `COMPLETED` in one commit. On any failure before the final write: delete nothing (nothing was written), set `FAILED` in a separate small transaction. This also fixes the connection-hogging problem.
  - Accept: kill the embedding provider mid-document → zero chunk rows exist for that document; document is `FAILED`; retrigger after provider recovery produces the full chunk set.

- [ ] **2.4 Ingestion-completed signal → subscriptions fire + caches invalidate.**
  - Defect: no event/notification exists between ingestor and bot (verified: no `@Scheduled`, no Redis, no HTTP client, no `publishEvent` in the ingestor). `TopicSubscriptionService.notifySubscribersForProduct` (`documentation-bot/.../TopicSubscriptionService.java:70-98`) has **zero callers** — users can subscribe but are never notified. Requirement 7's "cache invalidation + auto listing" currently only works by accident (product listing is a live DB query).
  - Fix: use Postgres `LISTEN/NOTIFY` on the shared DB (no new infra): ingestor `NOTIFY docai_ingestion_completed, '{tenantId, documentId, product, version, supersededDocumentId?}'` after the final commit of 2.3/2.1; bot runs a listener (dedicated connection, auto-reconnect) that (a) calls `notifySubscribersForProduct`, (b) evicts the per-tenant `TenantLLMConfig` cache is unrelated — instead evicts any future retrieval-adjacent caches, and (c) is the single hook where any later cache must register invalidation. Keep the payload minimal; re-read state from DB on receipt.
  - Accept: subscribe to a topic/product → upload a new version → notification row appears (and email digest picks it up); listener survives a Postgres restart (reconnects).

- [ ] **2.5 Stuck-`PROCESSING` reaper.**
  - Defect: a pod death mid-ingestion leaves the document `PROCESSING` forever; no timeout, no startup sweep; `/reprocess-failed` (`DocumentUploadController.java:204-229`) only targets `FAILED`.
  - Fix: `@Scheduled` job (ingestor, every 5 min): documents in `PROCESSING` older than a configurable threshold (default 30 min) → mark `FAILED` with error "ingestion timed out / interrupted", making them eligible for retrigger and `/reprocess-failed`. Add a startup sweep with the same rule.
  - Accept: set threshold to seconds in a test, create a fake `PROCESSING` row, observe it flip to `FAILED` and be retriggerable.

- [ ] **2.6 Fix the webhook/connector async-visibility race.**
  - Defect: `WebhookIngestionService.processEvent` (`:102-121`), `ConfluenceConnectorService.syncPage` (`:155-172`), `NotionConnectorService` (`:150-167`) are `@Transactional` and call `ingestionService.ingestUploadedFile` (`@Async`) with a document saved in the **still-open** transaction — the async thread's `findById` can miss the uncommitted row → "Document not found" → stuck `PROCESSING` (the upload controller avoids this only because it isn't `@Transactional`).
  - Fix: publish an application event and launch ingestion from an `@TransactionalEventListener(phase = AFTER_COMMIT)` (same pattern already used by the bot's `AnalyticsService.onQueryRecorded`), or `saveAndFlush` + move the async call outside the transaction boundary.
  - Accept: repeated webhook ingests never produce "Document not found" in logs; new integration test with a transaction-delaying hook.

- [ ] **2.7 Webhook event status must reflect ingestion outcome.**
  - Defect: `processEvent:116-121` marks the `WebhookEvent` `COMPLETED` **before** ingestion runs — the event reports success even when ingestion fails.
  - Fix: set `PROCESSING` at dispatch; update to `COMPLETED`/`FAILED` from the ingestion completion path (2.3/2.4).
  - Accept: a webhook pointing at a corrupt file ends `FAILED` with the error message on the event row.

- [ ] **2.8 Frontend: product/version lists auto-refresh.**
  - Defect: `ScopeChip.tsx:33-41` fetches `/api/products` once and caches in component state (`if (products.length > 0 || !token) return;`) — a newly ingested product/version is invisible until a full page reload. Same staleness class in `DocumentsTab`'s snapshot props.
  - Fix: covered by the react-query adoption in Phase 6.1 — `useQuery(['products'])` with `staleTime` ~60s + refetch-on-window-focus, and explicit `invalidateQueries(['products','documents'])` after any successful upload/retrigger in `DocumentsTab`. Until 6.1 lands, minimum patch: refetch on popover open.
  - Accept: upload a doc with a new version in one tab → open the scope chip → new version listed without reload.

**Verify (Phase 2):** Full lifecycle: upload → edit → re-upload same version (supersession, 2.1) → search reflects only new content (2.2) → kill provider mid-ingest (atomicity, 2.3) → reaper recovers a synthetic stuck row (2.5) → subscription notification fires on completion (2.4) → scope chip shows the new version without reload (2.8). `mvn test` both services.

---

## Phase 3 — Chat streaming + stop + error boundary  `[CRITICAL UX — requirement 1]`

**Goal:** Answers stream token-by-token like ChatGPT/Claude/Perplexity; users can stop generation; a render error never white-screens the app.

- [ ] **3.1 SSE streaming endpoint.**
  - Defect: no streaming anywhere in the repo (verified: zero matches for `SseEmitter|Flux<|StreamingResponseBody|text/event-stream|EventSource|WebSocket`). `ChatController.query` (`adapter/rest/ChatController.java:45-59`) blocks until the full `ChatResponse` is materialized — 5–30s of a bouncing-dots placeholder (`MessageItem.tsx:44-57`).
  - Fix: add `POST /api/chat/query/stream` returning `text/event-stream` via `SseEmitter`. Pipeline: run retrieval/re-ranking as today, then use Spring AI's streaming API (`chatClient.prompt(...).stream()`) — through the Phase 1 `LLMRouter` client — emitting events: `sources` (once, before generation, so citations render immediately), `token` (deltas), `done` (final message id, confidence, follow-ups), `error`. Persist the assistant message on completion exactly as the blocking path does (share the post-processing in `ChatService`). Keep the blocking endpoint for API v1/integration clients.
  - Accept: `curl -N` shows incremental `token` events; final persisted message identical in shape to the blocking path; rate limiting (`RateLimitFilter`, which matches `/api/chat/query`) also covers `/api/chat/query/stream`.
- [ ] **3.2 Frontend token-by-token rendering + stop.**
  - Defect: `sendChatMessage` (`src/services/chatService.ts:134-158`) awaits full JSON; no `AbortController` anywhere in `src`; send button is just disabled while waiting (`MessageInput.tsx:79-99`).
  - Fix: consume the SSE stream via `fetch` + `ReadableStream` reader (not `EventSource` — need POST + auth header), appending deltas into the assistant `MessageItem` (throttle re-renders ~30ms; `MarkdownContent` already handles partial markdown gracefully — verify code-fence flicker and buffer to line boundaries if needed). Render sources as soon as the `sources` event arrives. **Stop button** replaces send while streaming (aborts the controller; keep the partial answer with a "stopped" marker). Esc also stops.
  - Accept: visible progressive text on a real question; stop mid-answer keeps partial text and re-enables input; refresh mid-stream doesn't corrupt the session (message persisted server-side on completion or discarded cleanly on abort).
- [ ] **3.3 App-level React error boundary.**
  - Defect: zero error boundaries in the app (verified) — any render throw (e.g. malformed citation payload) white-screens the SPA.
  - Fix: `ErrorBoundary` component (logs, shows a branded "something went wrong" card with a reload action, uses existing `EmptyState`/`Button` primitives) wrapping the router in `App.tsx`, plus a nested boundary around `MessageList` so one bad message can't take down the chat shell.
  - Accept: a test component that throws renders the fallback, not a blank page; the rest of the shell (sidebar) stays interactive with the nested boundary.

**Verify (Phase 3):** Live: ask a long question → tokens stream; stop works; sources appear before the answer; a thrown render error shows the fallback. `npx tsc --noEmit && npm run build` clean.

---

## Phase 4 — Security hardening  `[HIGH — requirement 2]`

- [ ] **4.1 Login brute-force protection.**
  - Defect: `UserService.authenticate` (`application/service/UserService.java:34-49`) has no failed-attempt tracking; `/api/auth/login` is not rate-limited (RateLimitFilter only matches `/api/chat/query` — `config/RateLimitFilter.java:56`); no lockout fields exist anywhere (verified grep).
  - Fix: per-user failed-attempt counter + temporary lock (e.g. 10 failures → 15 min lock; store `failedAttempts`/`lockedUntil` on `users`, migration V19) **and** an IP-based bucket4j limit on `/api/auth/login` + `/api/auth/refresh` (reuse the existing bucket4j/Redis machinery in `RateLimitFilter`). Generic error message either way (no user-enumeration).
  - Accept: 11th bad password → 423/401 with lock semantics even with the *correct* password until expiry; login attempts from a hot IP throttle at the filter.
- [ ] **4.2 Dev JWT secret must not be able to reach production.**
  - Defect: base `application.yml:79` hardcodes a known secret ("my-secret-key-for-docai-system-jwt-tokens-2024" b64); only `application-prod.yml` requires `${JWT_SECRET}` — forget `SPRING_PROFILES_ACTIVE=prod` and tokens are forgeable.
  - Fix: startup guard (both services): if the active JWT secret equals the known dev constant AND profile isn't `dev`/`local`/`test`, fail boot with a clear message. Also fix the docker-compose default `dev-secret-change-in-prod-must-be-at-least-32-chars` (`docker-compose.yml:72`) — it isn't valid base64 (hyphens) unlike the yml default; replace with a valid-base64 dev value so out-of-the-box compose works deterministically.
  - Accept: boot with dev secret + no profile → hard failure naming the env var; compose up works with its default.
- [ ] **4.3 Hash invitation tokens at rest.**
  - Defect: `InvitationService.java:79-88` stores the raw token and looks it up via `findByToken` — unlike refresh tokens (SHA-256) and API keys (BCrypt). DB leak ⇒ usable invites.
  - Fix: store SHA-256(token) (same pattern as `RefreshTokenService`), look up by hash; the emailed URL keeps the raw token. Migration: existing pending rows can be invalidated (they expire in 72h anyway) — hash column replaces plaintext.
  - Accept: DB shows only hashes; existing accept-invite flow passes end-to-end.
- [ ] **4.4 Encrypt Confluence/Notion integration tokens at rest.**
  - Defect: `IntegrationToken.accessToken/refreshToken` are plaintext TEXT (`document-ingestor/.../domain/entity/IntegrationToken.java:44-48`).
  - Fix: encrypt/decrypt via the Phase 1.2 `SecretsCryptoService` (JPA `AttributeConverter` is the clean mechanism). One-time migration encrypts existing rows.
  - Accept: DB shows ciphertext; connector sync still works.
- [ ] **4.5 SSRF: lock down server-side fetches of caller-supplied URLs.**
  - Defect: `WebhookIngestionService.downloadAndStore` (`:136-162`) fetches arbitrary `downloadUrl` with redirects and no allowlist/size cap — internal services and cloud metadata (169.254.169.254) reachable; same shape in `ConfluenceConnectorService.fetchAllPages:98-101,183-189` (user-supplied `siteUrl` + attached bearer token).
  - Fix: shared `SafeUrlValidator` in the ingestor: https-only (allow http only for explicitly allowlisted hosts in dev), resolve DNS and reject private/link-local/loopback ranges (re-check after each redirect, cap redirects at 3), enforce a max download size (stream with a counting limit, e.g. 100MB) and connect/read timeouts. Confluence: require `siteUrl` host to end with `.atlassian.net` unless an env allowlist says otherwise.
  - Accept: webhook with `http://169.254.169.254/...` or `http://postgres:5432` → 400 rejected; oversized download aborts cleanly.
- [ ] **4.6 Real webhook authentication.**
  - Defect: `WebhookController.extractRequestedBy:104-114` only *logs* the X-API-Key — never validates it; the javadoc (`:26-29`) claims auth that doesn't exist. Actual auth is only the global JWT ADMIN filter.
  - Fix: implement per-webhook HMAC: `Webhook` already carries a `secret` — require `X-Webhook-Signature: sha256=<hmac(body)>` on `POST /api/webhooks/{id}/ingest`, verify with constant-time compare; delete the fake API-key logging. Keep JWT ADMIN for webhook CRUD.
  - Accept: unsigned/wrongly-signed ingest → 401; signed → 202. README's webhook section already documents exactly this contract — code now matches it.
- [ ] **4.7 API key validation: O(n) BCrypt scan + unenforced limits.**
  - Defect: `ApiKeyService.validateKey` (`:92-99`) does `findAll()` + BCrypt-match per key per request; the stored `keyPrefix` is never used to narrow; `ApiKey.rateLimitPerMin` is never enforced anywhere; `/api/v1/**` traffic bypasses `RateLimitFilter` entirely (it only inspects JWTs).
  - Fix: look up candidates by `keyPrefix` (indexed) then BCrypt-match the 1–2 candidates. Extend `RateLimitFilter` to key on API-key identity for `/api/v1/**`, honoring per-key `rateLimitPerMin` (fallback to global default).
  - Accept: validation does ≤2 BCrypt ops per request (assert via unit test on repository interaction); hammering `/api/v1/query` with one key → 429 at its configured limit.
- [ ] **4.8 Tighten public surface.**
  - Defect: `/actuator/prometheus` is in `PUBLIC_PATHS` (`config/SecurityConfig.java:37-54`) → unauthenticated scraping; `/api/auth/**` blanket-permits authenticated endpoints (`/me`, `/change-password`, `/my-tenants`, `/switch-tenant`, `/logout`).
  - Fix: require auth (or a scrape token / separate management port) for prometheus; enumerate the genuinely public auth paths (`/login`, `/refresh`, `/accept-invite`, `/oidc/**`, `/bootstrap` if present) instead of the wildcard.
  - Accept: anonymous `GET /actuator/prometheus` → 401; anonymous `GET /api/auth/me` → 401 at the filter (not a principal NPE); all existing flows still pass.
- [ ] **4.9 Audit-log coverage for sensitive mutations.**
  - Defect: audit rows exist only for API-key create/revoke, access grants, group ops. Missing: login (success/failure), tenant create/update/deactivate, LLM-config change (1.7), branding/retention change, GDPR erasure, invitation create/accept, tenant switch, password change.
  - Fix: add `AuditLogService` writes at each site (they all already know actor + tenant). Keep payloads free of secrets.
  - Accept: perform each action once → one audit row each, visible in the admin Audit Log tab.
- [ ] **4.10 Role hierarchy + guard hygiene.**
  - Defect: no `RoleHierarchy` bean → `SUPER_ADMIN` fails `hasRole('ADMIN')` checks (locked out of analytics/audit/etc. — functional bug, not a leak). `GapReportController` has no `@PreAuthorize` at all — guards are hand-rolled `if (!principal.isAdmin()) return 403` per method (fragile).
  - Fix: register `RoleHierarchy` (`SUPER_ADMIN > ADMIN > USER`) wired into method security; add class-level `@PreAuthorize("hasRole('ADMIN')")` to `GapReportController` (keep the tenant-scoping logic). **Caution:** re-check every `hasRole('ADMIN')` endpoint for tenant-scoping assumptions — SUPER_ADMIN has `tenantId = null`, so endpoints reading `principal.tenantId()` must handle it (403 or explicit tenant param) rather than NPE.
  - Accept: SUPER_ADMIN can hit admin endpoints without 403/NPE; a tenant ADMIN still cannot cross tenants.
- [ ] **4.11 Delete the deprecated unscoped search path.**
  - Defect: `VectorSearchService.search(query, product, version)` (`:129-156`) and `DocumentChunkRepository.findTopKSimilarAll/findTopKSimilarByProduct` (`:59,75`) have **no tenant filter**. Zero callers today (verified) — pure footgun.
  - Fix: delete the method + queries + the now-fully-dead `UserProductAccess`/`ProductAccessService`/`ProductAccessController`/`UserProductAccessRepository` stack (deprecated since Phase 2 of the archive; the admin UI no longer calls it) + drop table migration.
  - Accept: repo grep for `findTopKSimilarAll|UserProductAccess` → zero matches; build green.
- [ ] **4.12 Frontend token handling.**
  - Defect: refresh token in `localStorage` (`AuthContext.tsx:21-22,89-90`) — XSS-exfiltratable; tenant-switch in `CommandPalette.tsx:57-64` writes a hardcoded `'docai_token'` key and **never stores the new refresh token** (renewal desync after switching); audit `TenantSwitcher.tsx` for the same bug.
  - Fix: minimum — route tenant switch through a single `AuthContext.applyAuthResponse(...)` used by login/refresh/switch (fixes desync + duplication). Preferred — move the refresh token to an httpOnly SameSite cookie set by the backend (`/api/auth/refresh` reads cookie; access token stays in memory/localStorage); requires small backend change + CORS credentials review.
  - Accept: switch tenant → wait past access-token expiry → silent refresh still works; no hardcoded storage keys outside `AuthContext`.
- [ ] **4.13 Connector/webhook document quota enforcement.**
  - Defect: tenant `maxDocuments` is checked only in `DocumentUploadController:86-90`; Confluence/Notion sync and webhook ingestion create documents without any quota check.
  - Fix: extract the quota check into a shared guard called by all four creation paths.
  - Accept: a tenant at quota gets 429/409 from webhook and connector sync, not just uploads.

**Verify (Phase 4):** run the security-review skill on the diff; targeted curls for 4.1/4.5/4.6/4.8; full regression `mvn test` both services + live login/invite/switch-tenant flows.

---

## Phase 5 — Consistency & lifecycle jobs  `[HIGH/MED — requirement 6]`

- [ ] **5.1 GDPR deletion processor.**
  - Defect: `GdprController.requestDeletion` (`:44-56`) saves a `PENDING GdprDeletionRequest`; **nothing consumes the queue** (no `@Scheduled`, no listener — verified). The complete `GdprErasureService.eraseUser` exists and is only reachable via the manual admin endpoint.
  - Fix: `@Scheduled` daily job: process `PENDING` requests older than a grace period (e.g. 7 days, giving admins a cancel window), call `eraseUser`, mark `COMPLETED`; notify tenant admin on completion. Also cascade: revoke the user's share links + API keys at erasure (verify `eraseUser` covers them; add if not).
  - Accept: create a request, fast-forward the grace period in test → user data gone, request `COMPLETED`, audit row written (4.9).
- [ ] **5.2 Retention job must cover what its policy promises.**
  - Defect: `DataRetentionService` (`:40-66`) purges `query_session_graph`, `answer_feedback`, `audit_log` — but never `query_logs` (the table that grows on every query; `QueryLog.java:22`) and never `chat_sessions`/`chat_messages` despite the `chatSessionDays` policy field.
  - Fix: add tenant-scoped purges for `query_logs` (by `queryLogDays`) and chat sessions+messages (+ summaries, bookmarks referencing deleted messages — check FK cascade behavior) by `chatSessionDays`. Batch deletes (limit per pass) to avoid long locks.
  - Accept: seed old rows across two tenants with different policies → nightly run purges exactly per-tenant-policy rows.
- [ ] **5.3 Batch embeddings in the ingestor.**
  - Defect: `EmbeddingService.generateEmbeddings:59-61` embeds one text per API call, sequentially (`IngestionService:128-146`) — 1000 chunks = 1000 round-trips, inside the mega-transaction 2.3 removes.
  - Fix: OpenAI embeddings accept arrays — batch (e.g. 64 chunks/request, respecting token limits), keep the resilience wrappers per batch. Combined with 2.3 this makes large-document ingestion minutes → seconds.
  - Accept: ingesting a 500-chunk doc issues ≤ ~10 embedding requests (assert via wiremock counter); results identical.
- [ ] **5.4 Upload memory safety + parse bombs.**
  - Defect: `S3DocumentStorageService.store:50` does `readAllBytes()` (100MB × 8 concurrent = OOM vector; compose has no memory limits); `StructuredTextExtractor.extractPlainText:84` uses `BodyContentHandler(-1)` (unbounded) with no parse timeout — decompression-bomb DoS.
  - Fix: stream uploads to S3 (SDK supports content-length-known streaming; the existing `FileHashing.wrap()` DigestInputStream already streams — extend to avoid full buffering); cap `BodyContentHandler` at a sane limit (e.g. 50M chars, fail with a clear "document too large after extraction" error); run Tika parse inside a timeout (e.g. `ExecutorService` + 5 min cap).
  - Accept: a 100MB upload doesn't spike heap by 100MB×copies (observe via actuator metrics); a crafted expanding doc fails cleanly as `FAILED` with a readable error, service stays healthy.
- [ ] **5.5 PII quarantine.**
  - Defect: CRITICAL PII matches only log a warning (`IngestionService.java:113-120`) — the document still goes `COMPLETED`/searchable. Dead branch: `PiiDetectionService.java:92` checks a `"HIGH"` risk level no pattern sets (`:34-54`).
  - Fix: new status `QUARANTINED` for docs with CRITICAL findings (excluded from search by 2.2's status filter); admin PII-flags tab (exists: `PiiFlagsTab`) gains approve ("release" → `COMPLETED`) / reject (delete) actions + backend endpoints. Remove the dead `"HIGH"` branch.
  - Accept: upload a doc with a fake SSN → `QUARANTINED`, not searchable; admin release makes it searchable; audit rows for release/reject.
- [ ] **5.6 Forgot-password flow.**
  - Defect: none exists anywhere (verified grep); invite-only users who forget a password are permanently locked out (change-password requires the current password).
  - Fix: backend `POST /api/auth/forgot-password` (always 200 to prevent enumeration; emails a single-use 1h reset token — hashed at rest per 4.3's pattern; reuse `JavaMailSender` invite-email machinery) + `POST /api/auth/reset-password`; frontend `ForgotPasswordPage` + link on `LoginPage`; rate-limit the endpoint (4.1's IP bucket).
  - Accept: full loop works; token single-use and expiring; no user enumeration in responses.
- [ ] **5.7 Password policy.**
  - Defect: `@Size(min = 6)` only (`AuthController.java:151-152,177-178`; mirrored in frontend `minLength={6}`).
  - Fix: min 10 chars backend-validated on accept-invite/change/reset; frontend strength meter + show/hide toggle on all password inputs (LoginPage, AcceptInvitePage, ChangePasswordPage, ForgotPassword).
  - Accept: weak password rejected server-side with a readable message; meter reflects policy.
- [ ] **5.8 Async/context hygiene.**
  - Defect: `AsyncConfig` (bot) has no `TaskDecorator` — any future `@Async` reading `TenantContext`/MDC/SecurityContext silently gets nulls; `AutoFaqService.generateWeeklyFaq` calls its own `@Transactional` method (`:141-142`) through `this` — proxy bypassed, per-cluster transactionality is a no-op.
  - Fix: `TaskDecorator` propagating MDC + `TenantContext` snapshot (also needed by Phase 7.4 logging); split `generateFaqForCluster` into a separate `@Service` (or self-inject) so the proxy applies.
  - Accept: unit test proving decorated executor carries tenant/MDC; FAQ generation failure in one cluster no longer rolls back/aborts others.
- [ ] **5.9 Embedding cache: wire or delete.**
  - Defect: `EmbeddingCacheService` (Redis, 1h TTL) has zero callers (verified) and its key is `sha256(text)` only — no tenant/model scoping (`:50-61`), which matters once Phase 1.4 makes embedding models tenant-variable.
  - Fix (pick one): wire into the bot's query-embedding path keyed `emb::{model}::{sha256}` (tenant not needed if model is in the key — same text+model ⇒ same vector), or delete the class. Wiring is cheap and saves an API call on repeated questions — recommended.
  - Accept: repeated identical question → one embedding API call (wiremock counter) when Redis present; graceful no-cache behavior without Redis.

**Verify (Phase 5):** targeted tests per item (each Accept above); soak: ingest a 200-page PDF while watching heap + request counts; run retention + GDPR jobs against seeded fixtures.

---

## Phase 6 — Frontend excellence  `[HIGH — requirements 1 & 8]`

- [ ] **6.1 Adopt TanStack Query as the data layer.**
  - Defect: 100% raw `useEffect`+fetch (verified: no react-query/SWR, zero `AbortController` in `src`): duplicate fetches (`getTenantUsers` independently in `DocumentsTab.tsx:79-81`, `UsersPage.tsx:35`, `GroupsPage.tsx:50-52`), race conditions on fast chat switching (`useChatSessions.ts:68-86,178-197` — stale response can overwrite a newer selection), swallowed errors (`ChatPage` never renders `useChatSessions`'s `error` — `App.tsx:36-51`), and the Phase 2.8 stale-products bug.
  - Fix: install `@tanstack/react-query`; wrap app in a `QueryClientProvider`; migrate incrementally — highest-value first: products/scope (2.8), documents+ingestion-status (replace the unconditional 10s poll at `DocumentsTab.tsx:72-76` with `refetchInterval` only while any doc is `PROCESSING` and tab visible), tenant users/groups, chat sessions list. Keep service modules as the fetch functions. Surface `error` states via existing `EmptyState`.
  - Accept: no duplicate identical requests on a page load (network tab); chat switching under throttled network never shows the wrong history; sessions-load failure renders an error state, not an empty sidebar.
- [ ] **6.2 Home/discovery surface + document library (the "Google for the company" face).**
  - Defect: `/` drops into an empty chat (`ChatArea.tsx:110-125` renders a bare EmptyState); USER-role users have **no way to see what documents they can access** (the only surface is the ScopeChip's product/version list); the Ctrl+K palette only filters nav items — there is no content search UI at all.
  - Fix: (a) **Home**: replace the empty-chat state with a centered "ask anything" hero composer (submits into a new chat) + starter-prompt chips + a "recently updated documents" strip (from `/api/products` + document metadata) + recent chats. (b) **Library page** (`/library`, USER-visible): the user's accessible documents (new backend endpoint listing accessible document metadata — derive from `DocumentAccessPolicy.resolveScope` + ingestor `documents` metadata; paginated), grouped by product with version badges, searchable by title. (c) Palette gains a "Search docs: <query>" action that submits to chat scoped appropriately — full semantic search *is* chat here; don't build a parallel search engine.
  - Accept: a fresh USER logs in → sees what they can ask about and which docs they have; new-version uploads appear in "recently updated"; Playwright screenshots for light/dark.
- [ ] **6.3 Openable citations.**
  - Defect: `Source` (`src/types/index.ts:69-76`) has no URL/page/anchor; sources panel (`MessageItem.tsx:344-398`) is a dead-end excerpt. Note: today the ingestor **deletes** the stored object after successful ingestion (`IngestionService.processDocument` clears `storageKey`) — there is nothing to open.
  - Fix: (a) stop deleting stored objects on ingest success (S3 is the system of record; revisit the delete-after-ingest decision from archive Phase 3 — supersession in 2.1 now handles cleanup); (b) ingestor endpoint `GET /api/documents/{id}/download-url` → presigned MinIO/S3 URL (ADMIN or any user with a grant on the doc — enforce via the bot's access policy or a shared check); (c) `Source` gains `documentId`; sources panel gets an "Open document" action; PDFs render in a new tab natively, others download. Chunk-level page anchors are a stretch goal (requires page metadata at chunk time — note as follow-up, don't block).
  - Accept: click a citation → the actual source document opens; a user without a grant on that doc gets 403 on the URL endpoint.
- [ ] **6.4 ACL & user management at scale.**
  - Defect: `DocumentAccessManager.tsx:131-138` and `GroupsPage.tsx:272-279` use a native `<select>` over **every** tenant USER (unusable at 500 users); grantee lists unpaginated (`:163-178`); `UsersPage.tsx:108-126` is invite+read-only list — no search, no pagination, no role management, no deactivate/remove, no pending-invitations view; documents table unpaginated with no version grouping.
  - Fix: (a) shared `Combobox` primitive (Headless UI Combobox: typeahead, async search, virtualized options) replacing every user/group picker; (b) backend: `GET /api/admin/tenants/{id}/users` gains `?q=&page=&size=` (and same for groups/documents lists); (c) UsersPage: search + pagination + actions (promote/demote via new `PATCH /api/admin/users/{id}/role`, deactivate, remove) + a Pending Invitations section (new `GET /api/admin/invitations?status=PENDING` + revoke endpoint); (d) DocumentsTab: group rows by product (collapsible), version badges, "latest" indicator (Phase 2.1), server pagination, per-row delete (exists in ingestor? add `DELETE /api/documents/{id}` ADMIN-scoped with chunk+object cleanup if missing).
  - Accept: seeded 500-user tenant: pickers stay responsive with typeahead; invites can be revoked; role change round-trips; documents view stays usable with 200 docs.
- [ ] **6.5 Real analytics charts.**
  - Defect: hand-rolled div bars with no axes/legend/tooltips beyond hover (`OverviewTab.tsx:16-34`, `CostTrackingTab.tsx:11-31`); everything else is top-10 number lists; no date-range control.
  - Fix: adopt Recharts (fits React/Tailwind; **load the `dataviz` skill before writing any chart code**); rebuild Overview (query volume over time, top queries, coverage) and Cost (spend over time, by-model/user/product breakdowns) with proper axes/legends/tooltips, a date-range picker (7/30/90d) threading `from/to` params to the analytics endpoints (add params if missing), and CSV export buttons.
  - Accept: charts readable in both themes, keyboard/screen-reader accessible labels, date-range actually changes the data.
- [ ] **6.6 Chat shell: mobile + long-conversation performance + drafts.**
  - Defect: chat has no responsive breakpoint (`App.tsx:135-160` — sidebar+chat side-by-side always; Admin console has a mobile bar, chat doesn't); `MessageList.tsx:41-58` renders all messages and force-scrolls on every change (`:23-25`); drafts lost on chat switch (`MessageInput.tsx:17`); scope resets on every session change (`ChatArea.tsx:54-57` — intended per archive, keep).
  - Fix: (a) <768px: sidebar becomes an overlay drawer (reuse the AdminLayout mobile pattern); (b) virtualize the message list (`@tanstack/react-virtual`) and only autoscroll when the user is already at the bottom; (c) persist drafts per-chat in `sessionStorage`; (d) keyboard: `/` focuses composer, `Esc` stops generation (3.2), `↑` in empty composer edits last user message (see 6.7).
  - Accept: chat usable on a 375px viewport; a 300-message session scrolls at 60fps; draft survives switching chats.
- [ ] **6.7 Message editing.**
  - Defect: user messages are static (`MessageItem.tsx:226-227`); regenerate exists but edit-and-resend doesn't.
  - Fix: edit action on user messages → composer pre-filled → resend creates a new turn (do not rewrite history server-side — append, matching the existing regenerate semantics).
  - Accept: edit-resend produces a new answer; history remains coherent.
- [ ] **6.8 Polish sweep.**
  - `prefers-reduced-motion`: zero handling (verified) in an animation-heavy app — add a global `MotionConfig reducedMotion="user"` (framer-motion) + CSS media-query fallbacks. *(WCAG)*
  - Replace `window.confirm` in `PreferencesModal.tsx:82` and `ApiKeysPage.tsx:98` with the `Modal` primitive.
  - Per-route `document.title` (small `useDocumentTitle` hook; currently only `BrandingContext.tsx:76` sets it once).
  - PWA/meta: add `manifest.webmanifest`, apple-touch-icon, `robots.txt` under `public/`.
  - Remove dead `ui.chat.typingSpeed` from `config/app.json:16`; remove unused `refetchSessions` plumbing once 6.1 lands.
  - Audit-log tab: add date-range + actor filters (server params exist? add) alongside the existing action filter (`AuditLogTab.tsx:78-90`).
  - Accept: `npx tsc --noEmit && npm run build` clean; reduced-motion OS setting visibly disables entrance animations.

**Verify (Phase 6):** Playwright walkthrough (light+dark, desktop+375px): home → library → chat with streaming → citation open → admin (users at scale, charts, documents grouping). Zero console errors. Build clean.

---

## Phase 7 — Infra & operability  `[HIGH/MED]`

- [ ] **7.1 Fix the Helm chart (currently cannot store documents).**
  - Defect: ingestor reads `S3_ENDPOINT/S3_REGION/S3_ACCESS_KEY/S3_SECRET_KEY/S3_PATH_STYLE_ACCESS/S3_BUCKET` (`application.yml:104-112` → `S3Config.java:35-51`), but helm sets `AWS_REGION`/`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` and omits `S3_ENDPOINT` (`helm/doc-ai-system/templates/configmap.yaml:1-16`, `secrets.yaml:14-15`) → helm-deployed ingestor silently targets `http://minio:9000` with `minioadmin` defaults. Also: helm `OPENAI_BASE_URL` ends in `/v1` (`configmap.yaml:10`) → Spring AI double-appends → 404 (the exact bug fixed in `.env` back in archive Phase 0); `values.yaml:83` defaults embedding model to nonexistent `gpt-4o-embedding-4k` (same bad default in ingestor `application.yml:39` — fix both to `text-embedding-3-small`); helm secrets omit `SEED_ADMIN_PASSWORD`/`MAIL_*` that k8s manifests include.
  - Fix: sync helm templates/values to the (correct) raw `k8s/` manifests; delete the dead `app.aws.*` block from ingestor `application.yml:57-61`; fix the two bad defaults at the source.
  - Accept: `helm template` output env vars match `k8s/configmap.yaml:16-21` + `k8s/secrets.yaml:26-30` semantics; fresh helm install on kind/minikube can upload + retrieve a document.
- [ ] **7.2 Database backups + Postgres hardening.**
  - Defect: single-replica StatefulSet, no liveness probe, no backup mechanism of any kind (no CronJob/pg_dump/snapshot) for the DB holding **all tenants' data**.
  - Fix: k8s `CronJob` (nightly `pg_dump -Fc` → the existing S3/MinIO bucket under `backups/`, 14-day retention) in both k8s/ and helm; add liveness probe to the StatefulSet; document restore procedure in README; values.yaml toggle for "external managed DB" already exists — note backups are the operator's job in that mode.
  - Accept: CronJob produces a restorable dump (test a restore into a scratch DB).
- [ ] **7.3 Cluster/runtime hygiene.**
  - No `PodDisruptionBudget`s (compounds stuck-PROCESSING during drains — pairs with 2.5) and no `NetworkPolicy`s anywhere: add PDBs (minAvailable 1) for bot/ingestor and default-deny + explicit-allow NetworkPolicies (bot↔ingestor↔postgres↔redis↔minio, ingress→services).
  - `:latest` image tags (`values.yaml:10,29`, `k8s/document-ingestor-deployment.yaml:40`): parameterize to release tags.
  - Compose: add memory/cpu limits to both compose files (pairs with 5.4); `docker-compose.prod.yml` drops Redis while the bot expects it for distributed rate limiting — add Redis to prod compose (or document single-node fallback explicitly).
  - Delete stale `setup-database.sql` (unreferenced; contradicts Flyway; `init-db.sql` is the live one).
  - Accept: `kubectl get pdb,networkpolicy` non-empty; compose up with limits works; repo grep for `setup-database.sql` → nothing.
- [ ] **7.4 Make logs match the README's claims (tenantId/userId).**
  - Defect: README claims JSON logs carry `tenantId`/`userId`; in reality the **only** MDC write in either service is `traceId` (`RequestCorrelationFilter.java:38,43`); `logback-spring.xml` lists `documentId`/`spanId` MDC keys nothing populates.
  - Fix: after tenant/JWT resolution in both services' filters, `MDC.put("tenantId", ...)`/`MDC.put("userId", ...)` (clear in `finally`, mirroring `TenantContext`); ingestion pipeline sets `documentId` around processing; drop `spanId` from the encoder or add micrometer-tracing. Propagate via 5.8's TaskDecorator to async work.
  - Accept: one chat request and one ingestion produce JSON log lines carrying tenantId/userId (+documentId for ingestion).
- [ ] **7.5 CI hardening (`.gitlab-ci.yml`).**
  - Defect: CI builds frontend + runs `mvn test`; no image build/push, no lint (`npm run lint` exists), no `tsc --noEmit`, no dependency/security scan, ignores the 4 bot clients.
  - Fix: add stages — frontend lint+typecheck; docker image build (+push on tags) for bot/ingestor/frontend; `npm ci && npm test --if-present` for slack/teams bots + extension compile checks; dependency scan (e.g. `mvn org.owasp:dependency-check` or GitLab's built-in SAST/dependency scanning templates).
  - Accept: pipeline green end-to-end on a merge request; a failing `tsc` breaks the pipeline.
- [ ] **7.6 Small integration-client fixes.**
  - `browser-extension/manifest.json:36`: empty `host_permissions` — popup fetches to the backend depend on server CORS which won't include a `chrome-extension://` origin; add configurable host permission and document adding the extension origin to `CORS_ALLOWED_ORIGINS`.
  - slack/teams `docsClient.js:4`: `DOCS_API_KEY` defaults to `''` → confusing 401s; fail fast at startup with a clear message when unset.
  - Accept: extension works against a CORS-locked backend per its README; bots exit with a readable config error when unconfigured.
- [ ] **7.7 README/doc truth pass.**
  - Defect: README still documents removed/changed behavior: public `POST /api/auth/register` (removed — invite-only), "first admin via register" quick-start, `DEFAULT_TENANT_ID` fallback resolution order (removed — fails closed), local filesystem storage default (S3/MinIO only now), Redis embedding cache "1-hour TTL" (dead until 5.9), log-field claims (7.4).
  - Fix: rewrite the affected sections (Quick Start, Authentication, Multi-Tenancy, Configuration Reference, Monitoring) to match reality after Phases 1–6; add `SECRETS_ENCRYPTION_KEY` to the config table.
  - Accept: a newcomer can complete Quick Start verbatim on a clean machine.

**Verify (Phase 7):** fresh `helm install` on a local cluster passes an upload→ask round-trip; backup CronJob restore drill; JSON log inspection; CI pipeline green.

---

## Suggested build order & dependencies

1. **Phase 1** (LLM keys/routing) — unblocks the core product promise; `SecretsCryptoService` is reused by 4.3/4.4/5.6.
2. **Phase 2** (versioning/integrity/invalidation) — 2.2's status filter is assumed by 5.5's quarantine; 2.1's retained objects are assumed by 6.3's openable citations (do 6.3(a) with 2.1).
3. **Phase 3** (streaming + error boundary) — independent; highest visible UX win; do early.
4. **Phase 4** (security) — 4.1 reuses RateLimitFilter; 4.10's role hierarchy should land before building more admin endpoints in 6.4.
5. **Phase 5** (lifecycle jobs) — 5.8's TaskDecorator before 7.4's MDC propagation.
6. **Phase 6** (frontend) — 6.1 (react-query) first; it closes 2.8 and underpins every other item.
7. **Phase 7** (infra) — 7.1 is independent and CRITICAL if anyone deploys via helm: do it immediately if so.

Cross-cutting rules for every item: new endpoints get `@PreAuthorize` + tenant scoping + audit logging; new tables get `tenant_id` + index; new frontend surfaces use the `ui/` primitives + loading/empty/error states; every phase's Verify block runs before its boxes get `[x]`.
