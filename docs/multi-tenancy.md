# Multi-Tenancy

Docs-inator is fully multi-tenant. Every data table carries a `tenant_id` column, and all queries are automatically scoped — no data from one tenant is ever visible to another.

---

## Tenant Model

```
SUPER_ADMIN (platform-wide)
    │
    ├── creates Tenant A
    │       └── invites → ADMIN for Tenant A
    │                         ├── uploads documents
    │                         ├── invites users
    │                         └── grants document access to users / groups
    │
    └── creates Tenant B
            └── invites → ADMIN for Tenant B
                              └── ...
```

- **SUPER_ADMIN** is a platform-level role. The account has no tenant; it can create tenants and invite each tenant's first ADMIN, then steps back. Created once by `AdminSeeder` on first startup.
- **ADMIN** is tenant-scoped. Each tenant has one or more admins who manage that tenant's users, documents, and configuration.
- **USER** is tenant-scoped. Users can only search documents they have been explicitly granted access to.
- There is **no default tenant** and no fallback. A request without a resolved tenant is rejected if the endpoint requires one.

---

## Tenant Resolution

The current tenant is stored in `TenantContext` (ThreadLocal) for the lifetime of each request and cleared in a `finally` block to prevent leaks across requests.

**Resolution order:**

| Priority | Source | Notes |
|---|---|---|
| 1 | Authenticated principal's `tenantId` (from JWT or API key) | Authoritative. Cannot be overridden by a header — an authenticated caller cannot impersonate a different tenant. `null` for SUPER_ADMIN (intentionally platform-wide). |
| 2 | `X-Tenant-Id` header (UUID) | Only consulted when there is no authenticated principal (e.g. public branding lookup before login). |
| 3 | `X-Tenant-Slug` header (slug lookup) | Same — unauthenticated requests only. |

**Async propagation:** `ContextPropagatingTaskDecorator` is installed in both services' async executor config. It propagates `TenantContext`, MDC log fields (`tenantId`, `userId`, `traceId`), and the Spring Security context across thread pool hand-offs. SSE streams and `@Async` ingestion jobs always know the correct tenant.

---

## Creating a Tenant

Requires SUPER_ADMIN JWT.

```bash
curl -X POST http://localhost:8082/api/admin/tenants \
  -H "Authorization: Bearer <super-admin-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Acme Corp",
    "slug": "acme",
    "plan": "standard",
    "maxUsers": 50,
    "maxDocuments": 500,
    "adminEmail": "alice@acme.com"
  }'
```

This creates:
- The tenant record
- An invitation for `adminEmail` to become the first ADMIN (delivered by email if SMTP is configured)

---

## Managing Tenant Members

Tenant ADMINs invite users to their own tenant:

```bash
# Invite a user
curl -X POST http://localhost:8082/api/admin/users/invite \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"email":"bob@acme.com"}'

# List members
curl http://localhost:8082/api/admin/users \
  -H "Authorization: Bearer <admin-token>"

# Deactivate a member
curl -X POST http://localhost:8082/api/admin/users/<userId>/deactivate \
  -H "Authorization: Bearer <admin-token>"

# Revoke a pending invitation
curl -X DELETE http://localhost:8082/api/admin/invitations/<invitationId> \
  -H "Authorization: Bearer <admin-token>"
```

---

## Document Access Control

Documents belong to exactly one tenant and are invisible to every other tenant. Within a tenant, documents are not accessible to all users by default — access must be explicitly granted.

### Per-user grants

```bash
# Grant user access to a document
curl -X POST http://localhost:8082/api/admin/documents/<documentId>/access \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"userId":"<userId>"}'

# Revoke access
curl -X DELETE http://localhost:8082/api/admin/documents/<documentId>/access/<userId> \
  -H "Authorization: Bearer <admin-token>"
```

### Groups

Groups make it easy to grant a set of users access to a set of documents at once.

```bash
# Create a group
curl -X POST http://localhost:8082/api/admin/groups \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Engineering","description":"All engineers"}'

# Add a user to the group
curl -X POST http://localhost:8082/api/admin/groups/<groupId>/members \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"userId":"<userId>"}'

# Grant a group access to a document
curl -X POST http://localhost:8082/api/admin/documents/<documentId>/group-access \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"groupId":"<groupId>"}'
```

### How ACL is enforced at retrieval time

Before every vector search, `GrantBasedDocumentAccessPolicy.resolveScope(user)` computes a `SearchScope(tenantId, documentIds)` from:

1. Direct `document_access` table grants for the user
2. All groups the user belongs to via `group_memberships`, and those groups' `group_document_access` grants

The resulting `documentIds` set is injected into the pgvector similarity query as a SQL `IN` clause. Documents outside the user's scope are physically excluded from results, not just filtered from display.

---

## Tenant LLM Configuration

Each tenant can bring their own LLM API keys and choose their own models. Tenant ADMINs configure this via the admin panel or API.

```bash
curl -X PUT http://localhost:8082/api/admin/tenants/<tenantId>/llm-config \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "anthropic",
    "chatModel": "claude-sonnet-4-6",
    "apiKey": "<tenant-anthropic-key>",
    "embeddingProvider": "openai",
    "smartRouting": true,
    "simpleQueryModel": "gpt-4o-mini",
    "complexQueryModel": "gpt-4o"
  }'
```

The `apiKey` is encrypted at rest with AES-256-GCM before storage. Available providers: `openai`, `anthropic`, `azure_openai`.

---

## Tenant Branding

```bash
curl -X PUT http://localhost:8082/api/admin/tenants/<tenantId>/branding \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "logoUrl": "https://cdn.acme.com/logo.png",
    "primaryColor": "#0052cc",
    "productName": "Acme Docs"
  }'
```

---

## Data Retention

```bash
curl -X PUT http://localhost:8082/api/admin/tenants/<tenantId>/retention \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "queryLogRetentionDays": 90,
    "chatHistoryRetentionDays": 365
  }'
```

The nightly `DataRetentionService` (runs at 03:00 UTC) deletes records older than the configured retention period. See [Security — GDPR](security.md#gdpr-compliance) for user erasure.

---

## Multi-Workspace for Individual Users

A user can belong to more than one tenant (stored in `tenant_memberships`). Switch tenants without logging out:

```bash
curl -X POST http://localhost:8082/api/auth/switch-tenant/<targetTenantId> \
  -H "Authorization: Bearer <current-token>"
```

Returns a new JWT scoped to the target tenant.
