import { AuthResponse, TenantMembership } from '../types';

const BOT_URL = `${import.meta.env.VITE_BACKEND_URL || 'http://localhost:8082'}`;
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
