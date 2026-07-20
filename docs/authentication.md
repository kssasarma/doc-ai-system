# Authentication

Docs-inator supports three authentication methods. All are evaluated per request in the order listed below. The same JWT is valid on both services (they share `JWT_SECRET`).

---

## Method 1 — JWT (interactive users)

### Invitation-only account creation

There is no public self-registration endpoint. Every account is provisioned by invitation:

1. A SUPER_ADMIN creates a tenant and nominates an admin email.
2. The system emails an invitation link to that address.
3. The invitee follows the link and sets their username and password.
4. The tenant ADMIN can then invite additional users within their tenant.

This design prevents unauthorized tenant creation and ensures every account traces to a deliberate decision.

### Login

```bash
curl -X POST http://localhost:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"<password>"}'
```

Response:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 86400000
}
```

### Using the token

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### Token lifetimes

| Token | Default lifetime | Config variable |
|---|---|---|
| Access token | 24 hours | `JWT_EXPIRATION_MS` |
| Refresh token | 30 days | `JWT_REFRESH_EXPIRATION_MS` |

### Refreshing

```bash
curl -X POST http://localhost:8082/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"eyJhbGciOiJIUzI1NiJ9..."}'
```

### Password change

```bash
curl -X POST http://localhost:8082/api/auth/change-password \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"currentPassword":"old","newPassword":"new-strong-password"}'
```

### Password reset (forgot password)

```bash
# 1. Request a reset link (delivered by email if SMTP is configured)
curl -X POST http://localhost:8082/api/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com"}'

# 2. Complete the reset using the token from the email
curl -X POST http://localhost:8082/api/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{"token":"<token-from-email>","newPassword":"<new-password>"}'
```

### Login lockout

After `AUTH_MAX_FAILED_ATTEMPTS` consecutive failed attempts (default: 10), the account is locked for `AUTH_LOCKOUT_DURATION_MINUTES` (default: 15 minutes). Locked accounts return `423 Locked` with a `Retry-After` header.

---

## Method 2 — API Keys (integrations and CI/CD)

API keys allow non-interactive clients (Slack bot, CI/CD pipeline, browser extension) to authenticate without managing JWTs. Keys are stored hashed — the plaintext is only shown once on creation.

### Create a key

```bash
curl -X POST http://localhost:8082/api/user/api-keys \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{"name":"CI pipeline","expiresInDays":90}'
```

Response (save the `key` value — it is shown only once):

```json
{
  "id": "3fa85f64-...",
  "name": "CI pipeline",
  "key": "dak_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "createdAt": "2026-07-20T10:00:00Z",
  "expiresAt": "2026-10-18T10:00:00Z"
}
```

### Using a key

```http
X-API-Key: dak_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

### Revoking a key

```bash
curl -X DELETE http://localhost:8082/api/user/api-keys/<key-id> \
  -H "Authorization: Bearer <jwt>"
```

### Listing your keys

```bash
curl http://localhost:8082/api/user/api-keys \
  -H "Authorization: Bearer <jwt>"
```

Keys are scoped to the user who created them and inherit that user's tenant and role. Per-key rate limits apply in addition to the per-user rate limit.

---

## Method 3 — OIDC / SSO (enterprise tenants)

Docs-inator supports OpenID Connect for enterprise single sign-on. Supported providers include Google Workspace, Microsoft Azure AD, Okta, Auth0, and any OIDC-compliant IdP.

**JIT provisioning:** On first login through OIDC, the system automatically creates a local user account using the IdP's claims (`sub`, `email`, `name`). No pre-provisioning is needed.

### Setup (admin)

OIDC is configured per tenant. A tenant ADMIN navigates to **Admin → Tenant Settings → SSO** in the web UI and enters:

- Provider type (`google`, `azure`, `okta`, `generic_oidc`)
- Client ID
- Client Secret
- OIDC Discovery URL (e.g. `https://accounts.google.com/.well-known/openid-configuration`)
- Group mapping (optional): maps IdP groups to Docs-inator roles

### Login flow (API)

```bash
# 1. Fetch tenant OIDC configuration (public endpoint)
GET /api/auth/oidc/config?slug=acme

# Response includes authorization URL, client ID, scopes needed

# 2. After IdP authenticates the user, exchange the IdP claims for a Docs-inator JWT
curl -X POST http://localhost:8082/api/auth/oidc/callback \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "google",
    "tenantSlug": "acme",
    "claims": {
      "sub": "1234567890",
      "email": "alice@acme.com",
      "name": "Alice Smith",
      "picture": "https://..."
    }
  }'

# Response: {"token":"eyJ..."}
```

The returned token is a standard Docs-inator JWT and is used identically to password-based JWT tokens.

### Multi-workspace switching

A user who belongs to multiple tenants can switch without logging out:

```bash
curl -X POST http://localhost:8082/api/auth/switch-tenant/<tenantId> \
  -H "Authorization: Bearer <current-token>"
# Returns a new token scoped to the target tenant
```

---

## Roles

| Role | Scope | Capabilities |
|---|---|---|
| `SUPER_ADMIN` | Platform-wide | Create tenants, invite tenant admins, view platform metrics |
| `ADMIN` | Tenant | Manage users, upload documents, configure LLM routing, view analytics, manage ACLs |
| `USER` | Tenant | Chat with documents they have been granted access to |

SUPER_ADMIN has no tenant of its own. Tenant ADMIN cannot elevate their own role.

---

## Accepting an Invitation

```bash
curl -X POST http://localhost:8082/api/auth/accept-invite \
  -H "Content-Type: application/json" \
  -d '{
    "token": "<token-from-email>",
    "username": "alice",
    "password": "<password>"
  }'
```

Invitation tokens are single-use and expire after 7 days by default. An ADMIN can revoke pending invitations via the admin panel.
