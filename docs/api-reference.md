# API Reference

Both services expose an OpenAPI specification and a Swagger UI for interactive exploration.

| Service | Swagger UI | OpenAPI JSON |
|---|---|---|
| documentation-bot (port 8082) | http://localhost:8082/swagger-ui.html | http://localhost:8082/v3/api-docs |
| document-ingestor (port 8081) | http://localhost:8081/swagger-ui.html | http://localhost:8081/v3/api-docs |

---

## Authentication

All protected endpoints accept one of the following:

```http
Authorization: Bearer <jwt>
```

```http
X-API-Key: dak_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

See [Authentication](authentication.md) for how to obtain these credentials.

---

## documentation-bot (port 8082)

### Auth

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/auth/login` | None | Exchange credentials for JWT + refresh token |
| `POST` | `/api/auth/refresh` | None | Refresh an expired access token |
| `POST` | `/api/auth/accept-invite` | None | Complete account creation from an invitation token |
| `POST` | `/api/auth/forgot-password` | None | Request a password reset link (delivered by email) |
| `POST` | `/api/auth/reset-password` | None | Complete password reset using the emailed token |
| `GET` | `/api/auth/me` | JWT | Current user info |
| `POST` | `/api/auth/change-password` | JWT | Change the authenticated user's password |
| `GET` | `/api/auth/oidc/config` | None | Fetch tenant OIDC configuration by `?slug=` |
| `POST` | `/api/auth/oidc/callback` | None | Exchange IdP claims for an app JWT (JIT provisioning) |
| `POST` | `/api/auth/switch-tenant/{tenantId}` | JWT | Switch to a tenant the user belongs to |
| `POST` | `/api/auth/logout` | JWT | Invalidate the current refresh token |

### Chat

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/chat/sessions` | JWT | Create a new chat session |
| `GET` | `/api/chat/sessions` | JWT | List the user's chat sessions |
| `GET` | `/api/chat/sessions/{id}` | JWT | Get a single session with its messages |
| `DELETE` | `/api/chat/sessions/{id}` | JWT | Delete a session |
| `POST` | `/api/chat/sessions/{id}/messages` | JWT | Send a message; returns answer with citations |
| `GET` | `/api/chat/sessions/{id}/messages` | JWT | List messages in a session |
| `POST` | `/api/chat/sessions/{id}/messages/stream` | JWT | Send a message; streams the answer via SSE |
| `POST` | `/api/chat/sessions/{id}/pin` | JWT | Toggle session pin |
| `POST` | `/api/chat/sessions/{id}/rename` | JWT | Rename a session |
| `GET` | `/api/share/{shareToken}` | None (public) | View a shared chat session (read-only) |
| `POST` | `/api/chat/sessions/{id}/share` | JWT | Create a shareable link for a session |

### Public API v1 (API-key auth — for integrations)

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/v1/query` | API-Key | Query documentation; returns answer with citations |
| `GET` | `/api/v1/products` | API-Key | List products accessible to the key's owner |
| `GET` | `/api/v1/products/{id}/versions` | API-Key | List versions for a product |

**Query request body:**

```json
{
  "question": "How do I configure LDAP authentication?",
  "product": "MyProduct",
  "version": "2.1.0",
  "sessionId": "optional-for-multi-turn-context"
}
```

**Query response:**

```json
{
  "answer": "To configure LDAP authentication...",
  "confidence": "HIGH",
  "citations": [
    {
      "documentName": "Admin Guide",
      "product": "MyProduct",
      "version": "2.1.0",
      "chunkIndex": 47,
      "relevanceScore": 0.891,
      "excerpt": "LDAP authentication is configured in..."
    }
  ],
  "followUpQuestions": [
    "What LDAP attributes are required?",
    "How do I test the LDAP connection?"
  ]
}
```

### Intelligence

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/intelligence/multi-hop` | JWT | Multi-step reasoning for complex cross-section questions |
| `GET` | `/api/intelligence/people-also-asked/{sessionId}` | JWT | Related questions other users asked |
| `GET` | `/api/intelligence/version-diff` | JWT | What changed between two versions (`?product=&from=&to=`) |
| `GET` | `/api/intelligence/answer-evolution/{topic}` | JWT | How answers to a topic evolved across versions |
| `GET` | `/api/faq` | None (public) | Browse auto-generated FAQ clusters for a tenant |

### Bookmarks and Collections

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/user/bookmarks` | JWT | Bookmark a chat message |
| `GET` | `/api/user/bookmarks` | JWT | List bookmarks (supports `?tag=` filter) |
| `DELETE` | `/api/user/bookmarks/{id}` | JWT | Remove a bookmark |
| `POST` | `/api/user/collections` | JWT | Create a collection |
| `GET` | `/api/user/collections` | JWT | List collections |
| `POST` | `/api/user/collections/{id}/items` | JWT | Add an item to a collection |
| `DELETE` | `/api/user/collections/{id}/items/{itemId}` | JWT | Remove an item from a collection |

### Subscriptions and Notifications

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/user/subscriptions` | JWT | Subscribe to a topic or product |
| `GET` | `/api/user/subscriptions` | JWT | List subscriptions |
| `DELETE` | `/api/user/subscriptions/{id}` | JWT | Unsubscribe |
| `GET` | `/api/user/notifications` | JWT | Get in-app notifications |
| `POST` | `/api/user/notifications/{id}/read` | JWT | Mark a notification as read |

### User Settings

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/user/preferences` | JWT | Get the user's preferences |
| `PUT` | `/api/user/preferences` | JWT | Update preferences (verbosity, format, default product) |
| `GET` | `/api/user/api-keys` | JWT | List the user's API keys |
| `POST` | `/api/user/api-keys` | JWT | Create an API key |
| `DELETE` | `/api/user/api-keys/{id}` | JWT | Revoke an API key |

### Feedback

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/feedback` | JWT | Submit thumbs up / down + optional text on a message |
| `POST` | `/api/escalations` | JWT | Escalate a low-confidence answer to a product expert |

### GDPR (user self-service)

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/user/gdpr/export` | JWT | Download all personal data as a JSON archive |
| `DELETE` | `/api/user/gdpr/me` | JWT | Request account erasure (7-day grace period) |

### Admin (ADMIN role required)

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/admin/analytics/overview` | Query volume, top queries, answer quality |
| `GET` | `/api/admin/analytics/costs` | LLM cost per day, per user, per product |
| `GET` | `/api/admin/gap-reports` | Documentation gap analysis |
| `GET` | `/api/admin/escalations` | Escalated queries pending expert review |
| `POST` | `/api/admin/escalations/{id}/answer` | Submit an expert answer to an escalation |
| `GET` | `/api/admin/audit-logs` | Full immutable audit trail |
| `GET/POST` | `/api/admin/tenants` | List tenants / create a tenant (SUPER_ADMIN only) |
| `PUT` | `/api/admin/tenants/{id}/llm-config` | Configure LLM provider for a tenant |
| `GET/PUT` | `/api/admin/tenants/{id}/branding` | Manage tenant white-label branding |
| `PUT` | `/api/admin/tenants/{id}/retention` | Set data retention policy |
| `POST` | `/api/admin/users/invite` | Invite a user to the tenant |
| `GET` | `/api/admin/users` | List tenant users |
| `POST` | `/api/admin/users/{id}/deactivate` | Deactivate a user account |
| `GET` | `/api/admin/users/{id}/access` | View a user's document access grants |
| `POST/DELETE` | `/api/admin/documents/{id}/access` | Grant / revoke user document access |
| `POST` | `/api/admin/groups` | Create a group |
| `POST` | `/api/admin/groups/{id}/members` | Add a user to a group |
| `POST` | `/api/admin/documents/{id}/group-access` | Grant group access to a document |
| `GET` | `/api/admin/gdpr/deletion-requests` | Pending erasure requests |
| `POST` | `/api/admin/gdpr/deletion-requests/{id}/process` | Process a deletion request immediately |

---

## document-ingestor (port 8081)

### Document Upload and Management

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/ingest` | JWT | Upload a document file |
| `POST` | `/api/ingest/url` | JWT | Ingest a document from a URL |
| `GET` | `/api/documents` | JWT | List documents (`?product=&version=&status=`) |
| `GET` | `/api/documents/{id}` | JWT | Get a document with ingestion status |
| `DELETE` | `/api/documents/{id}` | JWT | Delete a document and all its chunks |
| `POST` | `/api/documents/{id}/approve-quarantine` | JWT (ADMIN) | Approve a PII-quarantined document for ingestion |

### Webhooks

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/webhooks` | JWT | Register a webhook for a product/version |
| `GET` | `/api/webhooks` | JWT | List registered webhooks |
| `DELETE` | `/api/webhooks/{id}` | JWT | Delete a webhook |
| `POST` | `/api/webhooks/{id}/ingest` | HMAC signature | Trigger ingestion from a CI/CD system |
| `POST` | `/api/v1/ingest/webhook` | HMAC signature | Platform-wide webhook endpoint |

### Connectors

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/connectors/confluence` | JWT | Register a Confluence integration |
| `POST` | `/api/connectors/confluence/sync` | JWT | Sync a Confluence space |
| `POST` | `/api/connectors/github` | JWT | Register a GitHub/GitLab webhook connector |
| `GET` | `/api/connectors` | JWT | List registered connectors |
| `DELETE` | `/api/connectors/{id}` | JWT | Remove a connector |

---

## Error Response Shape

All errors return a consistent JSON body:

```json
{
  "code": "DOCUMENT_NOT_FOUND",
  "message": "Document with id '3fa85f64-...' does not exist.",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

No stack traces are returned to clients. Include `traceId` when reporting bugs — it correlates to structured log entries on the server.

| HTTP status | Meaning |
|---|---|
| `400 Bad Request` | Invalid request body or parameters |
| `401 Unauthorized` | Missing or invalid credentials |
| `403 Forbidden` | Authenticated but insufficient role or missing document access grant |
| `404 Not Found` | Resource does not exist (or belongs to a different tenant) |
| `409 Conflict` | Duplicate resource (e.g. duplicate tenant slug) |
| `423 Locked` | Account is locked due to too many failed login attempts |
| `429 Too Many Requests` | Rate limit exceeded; includes `Retry-After` header |
| `503 Service Unavailable` | LLM circuit breaker is open; try again shortly |
