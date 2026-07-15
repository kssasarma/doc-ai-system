import { BACKEND_URL } from '../config/backend';
import { ApiResult } from '../types';

const BOT_URL = BACKEND_URL;

function authHeaders(token: string) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

export interface AuditLogEntry {
  id: string;
  actorId: string | null;
  actorUsername: string | null;
  action: string;
  targetType: string | null;
  targetId: string | null;
  metadata: string | null;
  ipAddress: string | null;
  createdAt: string;
}

export interface AuditLogPage {
  content: AuditLogEntry[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export async function fetchAuditLog(
  token: string,
  page = 0,
  size = 50,
  action?: string,
  since?: string,
  actorId?: string,
): Promise<ApiResult<AuditLogPage>> {
  try {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (action) params.set('action', action);
    if (since) params.set('since', since);
    if (actorId) params.set('actorId', actorId);
    const res = await fetch(`${BOT_URL}/api/admin/audit-log?${params}`, {
      headers: authHeaders(token),
    });
    const data = await res.json();
    if (!res.ok) return { success: false, error: data.message || 'Failed to fetch audit log' };
    return { success: true, data };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Network error' };
  }
}
