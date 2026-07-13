import { BACKEND_URL } from '../config/backend';

const BASE = `${BACKEND_URL}/api/intelligence`;

function authHeader(token: string): Record<string, string> {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

export interface VersionDiffResult {
  topic: string;
  product: string;
  versionA: string;
  versionB: string;
  added: string | null;
  modified: string | null;
  removed: string | null;
  breakingChanges: string | null;
  summary: string;
}

export interface VersionSnapshot {
  version: string;
  answer: string | null;
  confidence: number;
  hasDocumentation: boolean;
}

export interface EvolutionTimeline {
  question: string;
  product: string;
  snapshots: VersionSnapshot[];
  breakingSummary: string | null;
}

export async function getVersionDiff(
  token: string,
  topic: string,
  product: string,
  versionA: string,
  versionB: string
): Promise<{ success: boolean; data?: VersionDiffResult; error?: string }> {
  try {
    const res = await fetch(`${BASE}/version-diff`, {
      method: 'POST',
      headers: authHeader(token),
      body: JSON.stringify({ topic, product, versionA, versionB }),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}

export async function getEvolutionTimeline(
  token: string,
  question: string,
  product: string
): Promise<{ success: boolean; data?: EvolutionTimeline; error?: string }> {
  try {
    const params = new URLSearchParams({ question, product });
    const res = await fetch(`${BASE}/evolution?${params}`, { headers: authHeader(token) });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}
