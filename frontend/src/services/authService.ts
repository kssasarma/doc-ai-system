import { BACKEND_URL } from '../config/backend';
import { AuthResponse, TenantMembership } from '../types';

const BOT_URL = BACKEND_URL;
const AUTH_URL = `${BOT_URL}/api/auth`;

export async function login(username: string, password: string): Promise<AuthResponse> {
  const res = await fetch(`${AUTH_URL}/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  return res.json();
}

export async function getMe(token: string): Promise<AuthResponse> {
  const res = await fetch(`${AUTH_URL}/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error('Unauthorized');
  return res.json();
}

export async function changePassword(token: string, currentPassword: string, newPassword: string): Promise<AuthResponse> {
  const res = await fetch(`${AUTH_URL}/change-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ currentPassword, newPassword }),
  });
  const data = await res.json();
  if (!res.ok) return { error: data.error || `Password change failed: ${res.status}` };
  return data;
}

export async function listMyTenants(token: string): Promise<TenantMembership[]> {
  const res = await fetch(`${AUTH_URL}/my-tenants`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error('Failed to load tenants');
  return res.json();
}

export async function switchTenant(token: string, tenantId: string): Promise<AuthResponse> {
  const res = await fetch(`${AUTH_URL}/switch-tenant/${tenantId}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });
  const data = await res.json();
  if (!res.ok) return { error: data.error || `Switch failed: ${res.status}` };
  return data;
}

/** Silently renews a session — rotates the refresh token and mints a fresh access token,
 * without requiring the (possibly already-expired) access token at all. */
export async function refreshSession(refreshToken: string): Promise<AuthResponse> {
  const res = await fetch(`${AUTH_URL}/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });
  const data = await res.json();
  if (!res.ok) return { error: data.error || `Refresh failed: ${res.status}` };
  return data;
}

/** Best-effort server-side revocation of the refresh token — logout still clears local state
 * even if this fails (e.g. the network is already down), since that's what actually ends the
 * session from the user's point of view. */
export async function revokeSession(refreshToken: string): Promise<void> {
  try {
    await fetch(`${AUTH_URL}/logout`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
  } catch {
    // best-effort
  }
}

/** Always resolves successfully regardless of whether the email matches an account — the
 * backend deliberately gives no signal either way, to avoid user-enumeration. */
export async function forgotPassword(email: string): Promise<{ success: boolean }> {
  try {
    await fetch(`${AUTH_URL}/forgot-password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email }),
    });
  } catch {
    // Still report success to the caller — same no-signal-either-way reasoning as the backend.
  }
  return { success: true };
}

export async function resetPassword(token: string, newPassword: string): Promise<{ success: boolean; error?: string }> {
  const res = await fetch(`${AUTH_URL}/reset-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token, newPassword }),
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    return { success: false, error: data.error || `Reset failed: ${res.status}` };
  }
  return { success: true };
}
