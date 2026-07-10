import axios from 'axios';
import type { Invitation, AuthResponse } from '../types';

const BOT_URL = import.meta.env.VITE_BOT_API_URL ?? 'http://localhost:8082';

function headers(token: string) {
  return { Authorization: `Bearer ${token}` };
}

/**
 * Invite a new user. `tenantId` is only read by the backend when the caller is
 * SUPER_ADMIN (inviting a tenant's first ADMIN); it's ignored (and unnecessary)
 * when the caller is a tenant ADMIN inviting a USER into their own tenant.
 */
export async function inviteUser(token: string, email: string, tenantId?: string): Promise<Invitation> {
  const { data } = await axios.post<Invitation>(
    `${BOT_URL}/api/admin/invitations`,
    { email, tenantId },
    { headers: headers(token) },
  );
  return data;
}

export async function acceptInvite(token: string, username: string, password: string): Promise<AuthResponse> {
  const res = await fetch(`${BOT_URL}/api/auth/accept-invite`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token, username, password }),
  });
  const data = await res.json();
  if (!res.ok) {
    return { error: data.error || `Request failed: ${res.status}` };
  }
  return data;
}
