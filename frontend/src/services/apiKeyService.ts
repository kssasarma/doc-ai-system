import { BACKEND_URL } from '../config/backend';
import { ApiResult } from '../types';

const BOT_URL = BACKEND_URL;

function authHeaders(token: string) {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

export interface ApiKey {
  id: string;
  userId: string;
  keyPrefix: string;
  name: string;
  scopes: string[];
  rateLimitPerMin: number;
  revoked: boolean;
  lastUsedAt: string | null;
  expiresAt: string | null;
  createdAt: string;
}

export interface CreateKeyRequest {
  name: string;
  scopes?: string[];
  rateLimitPerMin?: number;
  expirationDays?: number;
}

export interface CreateKeyResponse {
  key: ApiKey;
  rawKey: string;
}

export async function fetchApiKeys(token: string): Promise<ApiResult<ApiKey[]>> {
  try {
    const res = await fetch(`${BOT_URL}/api/keys`, { headers: { Authorization: `Bearer ${token}` } });
    const data = await res.json();
    if (!res.ok) return { success: false, error: data.message || 'Failed to fetch keys' };
    return { success: true, data };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Network error' };
  }
}

export async function createApiKey(token: string, request: CreateKeyRequest): Promise<ApiResult<CreateKeyResponse>> {
  try {
    const res = await fetch(`${BOT_URL}/api/keys`, {
      method: 'POST',
      headers: authHeaders(token),
      body: JSON.stringify(request),
    });
    const data = await res.json();
    if (!res.ok) return { success: false, error: data.message || 'Failed to create key' };
    return { success: true, data };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Network error' };
  }
}

export async function revokeApiKey(token: string, keyId: string): Promise<ApiResult<void>> {
  try {
    const res = await fetch(`${BOT_URL}/api/keys/${keyId}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) {
      const data = await res.json().catch(() => ({}));
      return { success: false, error: data.message || 'Failed to revoke key' };
    }
    return { success: true };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Network error' };
  }
}
