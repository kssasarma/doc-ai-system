import { BACKEND_URL } from '../config/backend';
import { UserPreference } from '../types';

const BASE = `${BACKEND_URL}/api/users/preferences`;

function authHeader(token: string): Record<string, string> {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

export async function fetchPreferences(token: string): Promise<{ success: boolean; data?: UserPreference; error?: string }> {
  try {
    const res = await fetch(BASE, { headers: authHeader(token) });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}

export async function savePreferences(
  token: string,
  prefs: Partial<UserPreference>
): Promise<{ success: boolean; data?: UserPreference; error?: string }> {
  try {
    const res = await fetch(BASE, {
      method: 'PUT',
      headers: authHeader(token),
      body: JSON.stringify(prefs),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}
