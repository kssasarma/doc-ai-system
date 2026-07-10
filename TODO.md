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

## Phase 2 — Per-document, per-user access control

Today: `UserProductAccess` grants access by **product + version string**, globally, with no tenant scoping (`ProductAccessService.getAllUsersWithAccess()` lists every user in the system). There is no per-document ACL anywhere, and retrieval (`DocumentChunkRepository`) filters purely by `product`/`version` — never by tenant, never by grant.

- [ ] New `DocumentAccess` entity: `documentId`, `userId`, `tenantId`, `grantedBy`, `grantedAt`. Tenant admin grants access per document, per user, at upload time or after.
- [ ] `DocumentUploadController`: after a tenant admin uploads a doc, let them select which of their tenant's users get access (UI + API).
- [ ] Rewrite the retrieval queries (`DocumentChunkRepository.findTopKSimilar*`) to filter by `tenant_id = :tenantId AND document_id IN (:accessibleDocumentIds)`. **Decision: access-grant is the sole eligibility gate** — no manual product/version picking in the chat UI. `product`/`version` stay as descriptive metadata on the document (useful for the admin's own organization, coverage tooling, and citations shown with an answer) but are never required to retrieve a result.
- [ ] Update `ChatService`/`AnswerGenerationService`/`MultiHopReasoningService` to resolve "documents this user can see" once per query and search across all of them, instead of assuming a single product/version per chat session.
- [ ] Retire `UserProductAccess` in favor of `DocumentAccess` (a "grant everything under product X" convenience can be layered on top later as a bulk-grant UI action, not a separate access model).
- [ ] Add tests that prove tenant/user isolation at the query level (a user from tenant A, or without a grant, must get zero chunks from tenant B's or another user's documents — this is the one that must not regress silently).

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
2. **Phase 2** (document ACL + retrieval rewrite) — next up; depends on Phase 1's tenant plumbing, which is now in place.
3. **Phase 3** (storage) — independent of 2, can be done in parallel, but do it before or alongside Phase 4 so the upload UI is built once against the final storage backend.
4. **Phase 4** (frontend) — depends on the APIs from 1-3 being stable, since it's the UI over all of them. Also needs the Accept-Invite page flagged as still-pending in Phase 1.

## Decisions (resolved)

1. **Provisioning**: fully admin-provisioned, invite-by-email. No public self-registration.
2. **Retrieval**: pure access-grant based. No manual product/version picking in chat.
3. **Storage**: MinIO (off the app server, no cloud account needed), files retained to support the existing retrigger/reprocess feature.
