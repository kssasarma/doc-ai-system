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
  - [x] **Frontend**: "Accept Invite" page (`/accept-invite?token=...`) and removal of the public signup form — built in Phase 4 (`AcceptInvitePage.tsx`, `LoginPage.tsx`).
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

## Phase 3 — No files on the application server (done)

Was: `ingestor.storage.type` defaulted to `local` (`LocalDocumentStorageService`), backed by a Docker volume mounted into the container's own filesystem. `S3DocumentStorageService`/the whole `DocumentStorageService` abstraction existed but was **completely dead code** — grepping the codebase found zero callers; every real upload/ingest path (`DocumentUploadController`, `IngestionService`, `ConfluenceConnectorService`, `NotionConnectorService`, `WebhookIngestionService`) wrote directly to local disk via raw `Files`/`Paths` calls, bypassing the abstraction entirely. `documentation-bot` declared the AWS S3 SDK with no consumer at all.

- [x] **Actually wired up the pre-existing (but unused) `DocumentStorageService` abstraction** into all 5 places that touched local disk — this turned out to be the real scope of "Phase 3," not just pointing an already-integrated service at a new endpoint.
- [x] Added `exists(storageKey)` to the interface (needed by retrigger/ingest to check without a full download); deleted `LocalDocumentStorageService` outright (the resolved decision was to remove the disk option entirely, not leave it as a silent fallback).
- [x] `S3Config`: `S3Client` bean (custom endpoint + path-style access for MinIO; both are no-ops for real AWS S3 — just omit `S3_ENDPOINT` and set `path-style-access=false`), plus an `ApplicationRunner` that creates the target bucket on startup if missing (MinIO doesn't auto-create buckets).
- [x] `FileHashing` utility (`sha256Hex(byte[])`, and `wrap()`/`hexOf()` for streaming): every upload path now hashes **while** streaming into storage — `DocumentUploadController.upload()` and `WebhookIngestionService`'s download no longer touch local disk even transiently; `DigestInputStream` computes the SHA-256 in the same pass as the `PutObject`. Confluence/Notion sync (already in-memory strings) hash the byte array directly.
- [x] Adopted the `storage_key`/`storage_type` columns Phase 7's original migration (V6) had already added to `documents` but which — like the storage abstraction itself — no code ever actually populated. `file_path` is now `@Deprecated`, nullable, no longer written by new code; a new V8 migration just drops its `NOT NULL` constraint.
- [x] `IngestionService.processDocument`: on success, deletes the file from **storage** (not a local `File.delete()`) and clears `storageKey`/`storageType` — same "delete after ingest" behavior as before, just correctly targeting the authoritative stored copy instead of a local path. On failure, the stored copy is deliberately left in place so retrigger keeps working, exactly as it did pre-migration.
- [x] `ingestUploadedFile`: resolves a **local working copy** via `documentStorageService.resolve()` for Tika to read, and unconditionally deletes that working copy in a `finally` block (success or failure) — cleanly separating "the local temp copy this request created" from "the authoritative copy in storage," which only the success path touches.
- [x] Fixed two latent, previously-untested bugs this surfaced (this code had never actually run before, same story as the Flyway discovery in Phase 0):
  - `S3DocumentStorageService.resolve()` pre-created its destination temp file via `Files.createTempFile()`, then handed that already-existing path to the AWS SDK's `getObject(request, Path)`, which throws `FileAlreadyExistsException` on a pre-existing target. Fixed by deleting the (empty) placeholder immediately after reserving the unique filename, before the SDK writes to it.
  - `DocumentUploadController`/`WebhookIngestionService`'s duplicate-file-hash reuse path silently orphaned the previous local file when overwriting `filePath` on an existing record — now explicitly deletes the stale stored object first.
- [x] `docker-compose.yml`: added a `minio` service (bundled, auto-creates its bucket, healthchecked via `mc ready local`) — the convenient local-dev default. `docker-compose.prod.yml` deliberately does **not** bundle MinIO — a real production deployment should point `S3_ENDPOINT`/`S3_ACCESS_KEY`/`S3_SECRET_KEY` at its own properly-run object store (real AWS S3 or a dedicated MinIO deployment), not a single-node container tied to this compose file's lifecycle. Removed the now-unused `ingestor_uploads` volume from both compose files and the ingestor `Dockerfile`.
- [x] Removed `documentation-bot`'s dead AWS S3 SDK dependency and the `app.aws.*` properties nothing read. Also found and removed an unrelated leftover from Phase 1 while in the same file: `app.tenant.default-id`, a config property for the `DEFAULT_TENANT_ID` constant deleted back in Phase 1 that nobody had cleaned up from `application.yml`.
- [x] Updated/repaired tests that depended on the removed `calculateFileHash(File)` method and the old local-path assumptions: `DocumentUploadControllerTest` (also fixed pre-existing staleness — it was mocking repository methods the controller stopped calling back in Phase 1), `IngestionServiceIntegrationTest` (now mocks `DocumentStorageService`), new `FileHashingTest`. Also fixed `PostgresTestContainerBase`'s pgvector bootstrapping (see Phase 2) which these tests share.
- [x] **Verified live end-to-end**: uploaded a document → confirmed via `mc ls` inside the MinIO container that the object actually exists at the expected key (not on the ingestor container's disk) → confirmed chat retrieval returns the correct content → confirmed the object is deleted from MinIO once ingestion completes (`storage_key` cleared, `mc ls` empty) → manually placed a file in MinIO against a simulated `FAILED` document and confirmed `retrigger` correctly resolves it, reprocesses it, and cleans up afterward → confirmed `retrigger` on an already-`COMPLETED` document (file already gone) is correctly rejected.

### Deferred (separate follow-up work, not Phase 3)

- `documentation-bot` never wires up any document download/preview feature — nobody asked for one, so none was built (would be presigned-URL generation against MinIO if ever needed).

---

## Phase 4 — Production-quality tenant admin frontend (done)

Was: `frontend/src/components/Admin/` was a flat 12-tab client-state switcher (`AdminPanel.tsx`, no nested routes) — every tab was **globally scoped** (`TenantManagementTab` listed *all* tenants, `UserAccessTab` listed *all* users system-wide via a deprecated product/version grant API), with no tenant-admin-vs-super-admin split at all. `LoginPage` still had a dead `/register` form (backend endpoint removed in Phase 1). No bootstrap or accept-invite UI existed. No UI called `DocumentAccessController`, `InvitationController`, or the tenant-scoped `GET /api/admin/tenants/{id}/users` — all three were backend-ready but wired to nothing.

- [x] Split into two consoles, both driven by real `react-router` nested routes under `/admin/*` (`AdminEntry.tsx` picks by role; `AdminLayout.tsx` provides a shared sidebar/`Outlet` shell — desktop sidebar + horizontal tab strip on mobile, not the old tab-index `useState`):
  - **Super-admin console** (`SuperAdminConsole.tsx`): `/admin/tenants` only — tenant list/create (now collecting the required `adminEmail` the backend needs to fire the invite, which the old form silently omitted), activate/deactivate, per-tenant LLM config + retention (`TenantsPage.tsx`, rebuilt from the old `TenantManagementTab` — also fixed a pre-existing dead `input-sm` CSS class that was never defined anywhere, silently unstyled).
  - **Tenant admin console** (`TenantAdminConsole.tsx`): Overview/Documents/Coverage/Query Intel/FAQ/Gap Reports/Cost/Users/Audit Log/Escalations/GDPR/Settings, all scoped to the admin's own tenant.
- [x] New **Users** page (`UsersPage.tsx`) replaces the old global product/version `UserAccessTab`: tenant-scoped user list via `GET /api/admin/tenants/{id}/users`, plus an invite-a-user form wired to `POST /api/admin/invitations`.
- [x] New **Settings** page (`SettingsPage.tsx`): self-service LLM config + data retention + branding for the admin's own tenant (previously only reachable by a `SUPER_ADMIN` through the old global tenant tab).
- [x] Document upload flow gets a real access-grant step: `DocumentAccessManager.tsx` (new, shared component) shows immediately after a successful upload and is also reachable per-row via an "Access" action opening a modal — both drive the previously-unwired `DocumentAccessController` (`GET/POST/DELETE /api/documents/{id}/access`).
- [x] Auth rework: `POST /api/auth/register` UI removed entirely; added `BootstrapPage.tsx` (`/bootstrap`, first-`SUPER_ADMIN` setup, gracefully handles the "already initialized" `409`) and `AcceptInvitePage.tsx` (`/accept-invite?token=...`, handles expired/used-token `410` responses).
- [x] `AuthUser`/`AuthContext` extended for the 3-role/tenant model (`SUPER_ADMIN | ADMIN | USER`, nullable `tenantId`, `isSuperAdmin`).
- [x] End-user chat UI: verified manual product/version selection was **already removed** from the query flow during Phase 2 (no dropdowns, no params sent) — nothing left to do here.
- [x] Removed dead code enabled by the rework: `AdminPanel.tsx`, `TenantManagementTab.tsx`, `UserAccessTab.tsx`, `productAccessService.ts` (the last of these turned out to already be broken — it imported a nonexistent `ApiResult` type from `types/index.ts`, a pre-existing bug nobody had hit because nothing exercised that import path at build time; deleting it was a strict improvement, not just cleanup).
- [x] **Verified live end-to-end** with a headless-Chromium (Playwright) run against the real running stack, not just `vite build` — screenshotted every new page (login, bootstrap incl. already-initialized state, accept-invite incl. missing-token state, super-admin tenants, tenant-admin overview/documents/users/settings) and drove the document-access grant → revoke cycle against real data. This surfaced and fixed two real bugs that a build-only check would have missed:
  - **Relative `NavLink` accumulation**: sidebar nav items used relative `to="documents"` etc., which React Router resolves relative to the *currently matched route* rather than a fixed base — clicking between tabs accumulated path segments (`/admin/overview/documents/overview/…`) instead of navigating cleanly. Fixed by making every nav target and fallback `<Navigate>` an absolute `/admin/...` path.
  - **`tenantId` never populated**: `AuthContext` assumed the backend's `AuthResponse` (from `/login`, `/me`, bootstrap, accept-invite) included a `tenantId` field: it doesn't, on any of those endpoints, even though the JWT itself carries `tenantId` as a claim. Every tenant-scoped fetch (`Users`, `Settings`, the Documents tab's user picker) was silently short-circuiting on an empty tenant ID. Fixed by decoding `tenantId` client-side from the JWT payload instead of trusting the response body.
- [x] Confirmed no regressions: `npm run build` (the project's actual build gate — there's no `tsc -b` step) is clean; pre-existing unrelated issues noted but left alone as out of scope — a repo-wide `noUnusedLocals` violation pattern (bare `import React` with the automatic JSX runtime) predating this work, and an unrelated `/ask/api/notifications/unread-count` 404 from `NotificationPanel` hitting a malformed relative URL.

---

## Suggested build order

1. ~~**Phase 1** (tenant model + roles)~~ — done.
2. ~~**Phase 2** (document ACL + retrieval rewrite)~~ — done.
3. ~~**Phase 3** (storage)~~ — done.
4. ~~**Phase 4** (frontend)~~ — done. All four phases complete.

## Decisions (resolved)

1. **Provisioning**: fully admin-provisioned, invite-by-email. No public self-registration.
2. **Retrieval**: pure access-grant based. No manual product/version picking in chat.
3. **Storage**: MinIO (off the app server, no cloud account needed), files retained to support the existing retrigger/reprocess feature.
