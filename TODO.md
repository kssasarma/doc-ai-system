# doc-ai-system — Multi-Tenancy & Access Control Overhaul

Captures the gap between what exists today and the target model:
- A **super user** creates tenants and each tenant's first admin, then steps back (system metrics/audit is nice-to-have, not required now).
- A **tenant admin** configures their tenant's LLMs, uploads documents, and grants specific users access to specific documents.
- Documents belong to exactly one tenant and are invisible to every other tenant. Within a tenant, a document is only searchable by users explicitly granted access to it. **There is no default/fallback tenant.**
- Files are never stored on the application server's local disk.
- The tenant admin console must be a real, production-quality frontend — not a bare-bones placeholder.
- End users log in, ask questions, and get answers pulled from every document they personally have access to (no manual product/version picking).

Status legend: `[x]` done, `[ ]` not started, `[~]` in progress.

---

## Phase 0 — Immediate bugs (done)

- [x] Fixed `tenant_id NOT NULL` violation on document upload — `document-ingestor`'s `Document` entity was missing the `tenantId` field the migration added; all 5 document-creation sites were inserting `NULL`.
- [x] Fixed embedding/chat calls failing with `HTTP 404 - {"detail":"Not Found"}` — Spring AI's `OpenAiApi` hardcodes `/v1/chat/completions` and `/v1/embeddings` and appends them to `spring.ai.openai.base-url`. Both `.env` and the `application.yml` defaults had `base-url` already ending in `/v1`, doubling the path to `/v1/v1/...`. Fixed in `.env`, `.env.example`, and both services' `application.yml`.
- [x] **Discovered Flyway had never actually run in this environment.** Spring Boot 4 split Flyway autoconfiguration into a new `spring-boot-starter-flyway` module — both services only declared raw `flyway-core`/`flyway-database-postgresql`, so `FlywayAutoConfiguration` was never even a candidate (zero Flyway log output, ever, in either service). The live schema had instead been drifting via Hibernate `ddl-auto=update` historically, until that was switched to `none` in a recent commit — meaning no mechanism was applying schema changes at all going forward. Fixed by adding `spring-boot-starter-flyway` to both `pom.xml`s, and setting `baseline-version` to the last migration each service's schema already matched (12 for bot, 6 for ingestor) so Flyway correctly baselines existing installs instead of trying to replay `CREATE TABLE` migrations against tables that already exist — while still working normally (`baselineOnMigrate` is a no-op) against a genuinely empty/fresh database. Verified live: both services now log real Flyway migration activity and applied their next migration cleanly.

---

## Phase 1 — Foundational: real multi-tenancy, no default tenant (done)

Was: single seeded tenant (`00000000-...0001`), `TenantResolutionFilter` in `documentation-bot` fell back to it when unresolved, and `document-ingestor` had **no tenant resolution at all** — every document hard-coded to `Document.DEFAULT_TENANT_ID`. Roles were just `USER`/`ADMIN`, with "first user ever registered globally becomes ADMIN" (global, not tenant-scoped).

- [x] Added `SUPER_ADMIN` role (`User.Role`), `tenantId` now nullable on `User` (null only for `SUPER_ADMIN`).
- [x] Bootstrap flow: `POST /api/auth/bootstrap` — succeeds only when zero users exist, creates the first `SUPER_ADMIN`. Replaces the old "first user = ADMIN" hack.
- [x] **Removed public `POST /api/auth/register` entirely.** Fully admin-provisioned, invite-based accounts.
  - [x] `InvitationToken` entity + repository: `email`, `role`, `tenantId`, single-use, 72h expiry, `invitedBy`.
  - [x] `InvitationController` (`POST /api/admin/invitations`): `SUPER_ADMIN` invites a tenant's first `ADMIN` (must supply `tenantId`); `ADMIN` invites a `USER` into their own tenant (tenantId always forced to the caller's own — never trusted from the request body).
  - [x] `InvitationService` sends the invite email via the existing `spring-boot-starter-mail`/`JavaMailSender` setup (reused `DigestProperties` for from-address/app-url). Email failure doesn't fail invite creation (token still exists, logged for manual resend).
  - [x] `POST /api/auth/accept-invite` — token + username + password → activates the account with the invitation's role/tenant. Single-use (marks `acceptedAt`), rejects expired/reused tokens (410 Gone).
  - [x] `TenantController.create` now also invites the new tenant's first admin by email in the same call.
  - [ ] **Frontend still needed**: an "Accept Invite" page (`/accept-invite?token=...`) and removal of the public signup form — deferred to Phase 4 (frontend rework), backend is ready for it now.
- [x] `TenantContext.get()` now fails closed (`TenantNotResolvedException` → 400) instead of defaulting; `TenantContext.getOrNull()` added for the one legitimately tenant-optional endpoint (public branding lookup).
- [x] `TenantController` authorization split: tenant CRUD (list/create/update-plan) is `SUPER_ADMIN`-only; per-tenant config (branding/LLM/retention) is self-service — a tenant's own `ADMIN` may access only their own tenant's config, `SUPER_ADMIN` may access any (support case).
- [x] Gave `document-ingestor` its own tenant resolution: `TenantContext`/`TenantNotResolvedException` mirrored from the bot; `JwtTokenFilter` now extracts the `tenantId` JWT claim and populates it per-request.
- [x] Replaced every `Document.DEFAULT_TENANT_ID` usage:
  - `DocumentUploadController` — real per-request tenant; also made file-hash dedup/reuse, the document list, and retrigger **tenant-scoped** (these were previously global — e.g. two tenants uploading byte-identical files would have collided on the same row; `retrigger`/list endpoints leaked across all tenants).
  - `ConfluenceConnectorService`/`NotionConnectorService` — added `tenantId` to `IntegrationToken` (captured at OAuth-connect time from the connecting admin's `TenantContext`), synced documents now inherit the token's tenant.
  - `WebhookIngestionService` — added `tenantId` to `WebhookEvent`, captured synchronously in `createEvent` (on the request thread) since `processEvent` runs `@Async` on a different thread where the request's `ThreadLocal` `TenantContext` isn't available. Also tightened `SecurityConfig` so webhook ingestion requires real JWT auth (it was previously a blanket-public path that only *logged* the caller without validating anything).
- [x] Deleted the `DEFAULT_TENANT_ID` constant — zero remaining references.
- [x] Removed watched-folder ingestion entirely (`DirectoryWatcher` component, `IngestionService.ingestDocument(File)`, the now-dead `FileMetadata` model, `ingestor.watch-directory`/`INGEST_DIR` config, and the `ingestor_watched` Docker volume in both compose files) — it had no request context to resolve a tenant from, and was a local-disk mechanism Phase 3 was going to remove anyway.
- [x] No data migration needed for the existing seeded "Default" tenant — it remains a completely ordinary tenant (tenant #1) going forward; nothing in code treats it specially anymore.
- [x] Verified live end-to-end: bootstrapped a `SUPER_ADMIN` → created a tenant (auto-invited its admin) → accepted the invite → uploaded a document as the new tenant admin → confirmed the document landed under the *new* tenant's ID, not the old default — full isolation confirmed at the write path. Also confirmed: old `/register` now 404s, reusing an invite token now 410s, a tenant admin hitting `SUPER_ADMIN`-only endpoints now 403s.

---

## Phase 2 — Per-document, per-user access control (done)

Was: `UserProductAccess` granted access by product/version string, globally, with no tenant scoping, and was never actually checked anywhere at retrieval time — grepping the whole codebase for it turned up nothing outside its own admin CRUD stack. Retrieval (`DocumentChunkRepository`) filtered purely by `product`/`version` — never by tenant, never by any access grant. Any authenticated user could query any product/version's documents.

- [x] `DocumentAccess` entity + `document_access` table (V14 migration): `documentId`, `userId`, `tenantId`, `grantedBy`, `grantedAt`. No FK to `documents(id)` — that table is document-ingestor-owned and the two services migrate independently with no startup ordering guarantee (same reason ingestor's own migrations never FK-reference bot-owned tables); enforced at the application layer instead.
- [x] **SOLID abstraction for eligibility** (the actual point of "no pain points for future features"):
  - `SearchScope` (record: `tenantId` + `documentIds`) — the one value every retrieval call needs.
  - `DocumentAccessPolicy` interface + `GrantBasedDocumentAccessPolicy` — the single seam between "who can see what" and "how retrieval executes." A future access model (team-based, role-based blanket grants, etc.) is a new implementation of this interface, swapped in via Spring — it never touches `VectorSearchService`, `ChatService`, or `MultiHopReasoningService`.
  - `GrantBasedDocumentAccessPolicy`: `USER` → exactly their granted documents; `ADMIN` → their whole tenant's corpus implicitly (they manage it — requiring a personal grant on every document they uploaded themselves would be pure friction).
- [x] `DocumentAccessService` (grant/revoke/list) + `DocumentAccessController` (`POST/DELETE/GET /api/documents/{id}/access`, `ADMIN`-only) — validates both the document and the target user belong to the caller's own tenant before granting (an admin from tenant A can't touch tenant B's documents or users, even by guessing a UUID).
- [x] `TenantController` gained `GET /{id}/users` (tenant-scoped user list) to back the grant-picker UI Phase 4 will build.
- [x] `VectorSearchService.search(query, SearchScope)` — the new, sole eligibility-gated path; short-circuits (skips even generating an embedding) when the scope is empty. `DocumentChunkRepository.findTopKSimilarAccessible` filters `d.tenant_id = :tenantId AND d.id IN (:documentIds)` — the tenant_id check is defense-in-depth even against a hypothetical corrupted/leaked scope.
- [x] `ChatService` (both the single-shot and multi-hop paths, plus `regenerateAnswer`) and `MultiHopReasoningService` now resolve `SearchScope` once per request and use it — no manual product/version picking gates anything anymore. product/version remain descriptive-only (still shown on sources, still used to enrich the multi-hop decomposition prompt).
- [x] `ApiV1Controller`'s `/search` endpoint migrated to the same scope-based path (`/query` already covered — it delegates to `ChatService`).
- [x] **Deliberately scoped down**: `AnswerEvolutionService`, `AutoFaqService`, `TopicSubscriptionService`, and `VersionDiffService` also call vector search but for admin/system-wide analysis, not a specific end user's question — and their own underlying entities (`QuerySessionGraph`, `TopicSubscription`, `FaqEntry`/`FaqCluster`) have **zero tenant_id columns at all**, a separate, pre-existing gap. Retrofitting those is real, separate work (add tenant_id to ~4 entities, loop per-tenant in the scheduled `AutoFaqService` job, etc.) — out of scope for "per-document access for chat." Left them on the old `VectorSearchService.search(query, product, version)` path, now explicitly marked `@Deprecated` with a javadoc naming exactly why and what not to use it for, so the gap is visible rather than silently inherited.
- [x] `UserProductAccess`/`ProductAccessService`/`ProductAccessController`/`UserProductAccessRepository` marked `@Deprecated` with javadoc pointing at the replacement — not deleted outright, since the current admin UI ("Users & Access" tab) still calls them and Phase 4 hasn't rebuilt it yet. Never consulted by retrieval.
- [x] Fixed a real bug surfaced during verification: `DocumentAccessService.grant()` read `@CreationTimestamp`-generated `grantedAt` immediately after `save()`, which Hibernate doesn't guarantee is populated in-memory until a flush — switched to `saveAndFlush()`.
- [x] Fixed `PostgresTestContainerBase` (shared integration test base): it disabled nothing and ran production Flyway migrations, which don't create `documents`/`document_chunks` (ingestor-owned) — a latent gap only exposed now that Flyway actually runs and Hibernate `ddl-auto` is `none`. Tests now build their schema straight from current `@Entity` definitions (`ddl-auto: create-drop`, Flyway disabled) and the pgvector extension is created via a real Testcontainers init script instead of a stale comment claiming it already happened.
- [x] `DocumentAccessIsolationTest` (new): no-grant → empty scope → search skipped entirely without even calling the embedding model; grant → sees only that document; `ADMIN` → sees the whole tenant corpus with zero explicit grants; a deliberately corrupted cross-tenant `SearchScope` is still blocked by the query's own tenant filter. Could not execute in this sandbox — Testcontainers' Docker client here negotiates API v1.32 against a daemon requiring 1.41+ (pre-existing environment limitation, same reason the older `VectorSearchServiceIntegrationTest` also shows as skipped, not failing).
- [x] **Verified live end-to-end instead**, against the real running stack: bootstrap → create tenant → invite tenant admin → accept → tenant admin invites a `USER` → accept → admin uploads a document → **USER asks about it before any grant → correctly gets nothing** → admin grants access → **USER asks again → correct chunk retrieved, right content** → **ADMIN asks with zero explicit grants → also sees it** (implicit tenant-wide) → revoke → **USER asks again → back to nothing**. Every step matched the intended behavior exactly.
- [x] Found and documented (not fixed — unrelated, pre-existing, doesn't affect the user-facing response) a separate async/transaction-timing bug: `AnalyticsService.logQuery`'s `@Async` write to `query_logs` can race the owning `@Transactional` request and fail on `query_logs_session_id_fkey` after the chat response has already been returned to the client. Worth a follow-up ticket.

### Deferred (separate follow-up work, not Phase 2)

- Add `tenant_id` to `QuerySessionGraph`, `TopicSubscription`, `FaqEntry`, `FaqCluster` and retrofit `AnswerEvolutionService`/`AutoFaqService`/`TopicSubscriptionService`/`VersionDiffService` onto tenant-scoped (not user-grant-scoped) search — they're admin/system tools, not end-user chat, so `SearchScope` would need a "tenant-wide, no per-user filter" variant alongside the grant-based one.
- Fix the `AnalyticsService.logQuery` async/transaction race noted above.
- Physically delete `UserProductAccess` and friends once Phase 4 rebuilds the admin UI against `DocumentAccessController`.

---

## Phase 3 — No files on the application server

Today: `ingestor.storage.type` defaults to `local` (`LocalDocumentStorageService`), backed by Docker volumes (`ingestor_watched`, `ingestor_uploads`) mounted into the `document-ingestor` container's own filesystem. `S3DocumentStorageService` is fully implemented but inactive (needs `STORAGE_TYPE=s3`, never set in `docker-compose.yml`). `documentation-bot` declares the AWS S3 SDK dependency but no code anywhere uses it — dead dependency.

**Decision: keep the source file, but move it off the app server, on MinIO.** Considered deleting files entirely post-ingestion (nothing left to store), but `document-ingestor` already has a retrigger/reprocess feature (`DocumentUploadController.retrigger`, `IngestionService.prepareRetrigger`) that re-parses from the stored file without requiring re-upload — deleting the file post-ingestion would silently break that capability. MinIO satisfies "not on the app server" (it's its own container/volume) without needing a real AWS account.

- [ ] Add a `minio` service to `docker-compose.yml` (image `minio/minio`, its own volume, console port for admin inspection).
- [ ] Point `S3DocumentStorageService` at MinIO via its endpoint-override support (S3 SDK v2 supports custom endpoints) — set `STORAGE_TYPE=s3` plus a MinIO endpoint/credentials/bucket, and confirm it path-styles correctly (MinIO needs path-style access, not virtual-hosted-style).
- [ ] Switch default storage to `s3` and remove the `local` option so it can't silently regress back to disk.
- [ ] Remove `ingestor_watched`/`ingestor_uploads` local volumes from `docker-compose.yml` once local storage is gone.
- [ ] Rework watched-folder ingestion (Phase 1 also flags this for tenant-resolution reasons) — drop it; it's both a local-disk mechanism and tenant-less.
- [ ] Wire up or remove `documentation-bot`'s unused S3 dependency — if the bot needs to let users view/download the original source document, implement presigned URL generation against MinIO; otherwise delete the dependency.
- [ ] Confirm uploaded files never linger in a local temp path after the `PutObject` succeeds (check `DocumentUploadController`'s `tempFile` handling — currently writes to local disk before upload; that transient file must be deleted immediately after the object-store write, success or failure).

---

## Phase 4 — Production-quality tenant admin frontend

Today: `frontend/src/components/Admin/` is a flat 12-tab client-state switcher (no nested routes), React + TypeScript + Vite + Tailwind. Tabs are substantive (100-270 lines, live API calls) but every tab is **globally scoped** — `TenantManagementTab` lists all tenants, `UserAccessTab` lists all users — there is no tenant-admin-vs-super-admin split at all.

- [ ] Split into two consoles:
  - **Super-admin console**: tenant list/create + (later, optional) system metrics/audit. Nothing else.
  - **Tenant admin console**: everything scoped to the admin's own tenant — LLM config, document upload + per-user access grants, tenant's own user list, FAQ/gap/coverage/cost/escalation tooling that already exists.
- [ ] Real routing (`react-router` nested routes under `/admin/*`, not tab-index client state) so pages are linkable/refreshable.
- [ ] Document upload flow gets a real access-grant step (multi-select tenant users, not just an upload form).
- [ ] Proper loading/error/empty states, form validation, and responsive layout across the reworked pages — this is the "not a dumb chip here and there" bar the user asked for; budget real design/UX time here, not a mechanical port of the existing tabs.
- [ ] End-user chat UI: remove manual product/version selection: the underlying question just searches everything the logged-in user has access to (depends on Phase 2's retrieval rework).

---

## Suggested build order

1. ~~**Phase 1** (tenant model + roles)~~ — done.
2. ~~**Phase 2** (document ACL + retrieval rewrite)~~ — done.
3. **Phase 3** (storage) — next up; independent of 1/2, can be done in parallel with Phase 4 planning, but do it before or alongside Phase 4 so the upload UI is built once against the final storage backend.
4. **Phase 4** (frontend) — depends on the APIs from 1-3 being stable, since it's the UI over all of them. Needs: the Accept-Invite page (Phase 1), the document-upload access-grant step against `DocumentAccessController` (Phase 2), and the final storage backend (Phase 3).

## Decisions (resolved)

1. **Provisioning**: fully admin-provisioned, invite-by-email. No public self-registration.
2. **Retrieval**: pure access-grant based. No manual product/version picking in chat.
3. **Storage**: MinIO (off the app server, no cloud account needed), files retained to support the existing retrigger/reprocess feature.
