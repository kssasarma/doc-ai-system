import { BACKEND_URL } from '../config/backend';

const BASE = `${BACKEND_URL}/api/user/gdpr`;

interface ApiResult<T> { success: boolean; data?: T; error?: string; }

/** GDPR Article 20 — downloads a JSON export of everything held for the current user. */
export async function exportMyData(token: string): Promise<ApiResult<void>> {
  try {
    const res = await fetch(`${BASE}/export`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'my-data.json';
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    return { success: true };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}

/** GDPR Article 17 — files a deletion request for the current user's account, processed by an admin. */
export async function requestAccountDeletion(token: string): Promise<ApiResult<void>> {
  try {
    const res = await fetch(`${BASE}/me`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}
