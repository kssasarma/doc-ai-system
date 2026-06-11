import { ApiResult } from '../types';

const BOT_URL = `${import.meta.env.VITE_BOT_URL || 'http://localhost:8082'}`;

function authHeaders(token: string) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

export interface ProductAccessGrant {
  id: string;
  userId: string;
  username: string;
  product: string;
  version: string | null;
  grantedBy: string | null;
  createdAt: string;
}

export interface UserWithAccess {
  userId: string;
  username: string;
  email: string;
  role: string;
  grants: ProductAccessGrant[];
}

async function request<T>(method: string, path: string, token: string, body?: object): Promise<ApiResult<T>> {
  try {
    const res = await fetch(`${BOT_URL}${path}`, {
      method,
      headers: authHeaders(token),
      body: body ? JSON.stringify(body) : undefined,
    });
    const data = await res.json();
    if (!res.ok) return { success: false, error: data.message || 'Request failed' };
    return { success: true, data };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Network error' };
  }
}

export const fetchAllUsersWithAccess = (token: string) =>
  request<UserWithAccess[]>('GET', '/api/admin/product-access/users', token);

export const grantAccess = (token: string, userId: string, product: string, version?: string) =>
  request<ProductAccessGrant>('POST', '/api/admin/product-access', token, { userId, product, version: version || null });

export const revokeAccess = (token: string, grantId: string) =>
  request<void>('DELETE', `/api/admin/product-access/${grantId}`, token);
