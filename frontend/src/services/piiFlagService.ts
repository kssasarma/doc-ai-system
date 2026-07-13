import { INGESTOR_URL } from '../config/backend';
import { PiiFlag } from '../types';

const BASE = `${INGESTOR_URL}/api/pii-flags`;

interface ApiResult<T> { success: boolean; data?: T; error?: string; }

function authHeaders(token: string): Record<string, string> {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

export async function fetchPiiFlags(token: string, reviewed = false): Promise<ApiResult<PiiFlag[]>> {
  try {
    const res = await fetch(`${BASE}?reviewed=${reviewed}`, { headers: authHeaders(token) });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}

export async function reviewPiiFlag(
  id: string,
  actionTaken: 'ACKNOWLEDGED' | 'REDACTED' | 'DISMISSED',
  token: string
): Promise<ApiResult<PiiFlag>> {
  try {
    const res = await fetch(`${BASE}/${id}/review`, {
      method: 'PATCH',
      headers: authHeaders(token),
      body: JSON.stringify({ actionTaken }),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}
