import { BACKEND_URL } from '../config/backend';

const BASE = `${BACKEND_URL}/api/subscriptions`;

function authHeader(token: string): Record<string, string> {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

export interface TopicSubscription {
  id: string;
  topic: string;
  product: string | null;
  version: string | null;
  createdAt: string;
}

export async function getSubscriptions(
  token: string
): Promise<{ success: boolean; data?: TopicSubscription[]; error?: string }> {
  try {
    const res = await fetch(BASE, { headers: authHeader(token) });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}

export async function createSubscription(
  token: string,
  topic: string,
  product?: string,
  version?: string
): Promise<{ success: boolean; data?: TopicSubscription; error?: string }> {
  try {
    const res = await fetch(BASE, {
      method: 'POST',
      headers: authHeader(token),
      body: JSON.stringify({ topic, product: product ?? null, version: version ?? null }),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}

export async function deleteSubscription(
  id: string,
  token: string
): Promise<{ success: boolean; error?: string }> {
  try {
    const res = await fetch(`${BASE}/${id}`, { method: 'DELETE', headers: authHeader(token) });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}
