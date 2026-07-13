import { BACKEND_URL } from '../config/backend';

const BASE = `${BACKEND_URL}/api/admin/gap-reports`;

function authHeader(token: string): Record<string, string> {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

export interface GapReport {
  id: string;
  product: string | null;
  version: string | null;
  reportPeriodStart: string;
  reportPeriodEnd: string;
  totalLowConfidenceQueries: number;
  gapTopics: string; // JSON string
  generatedAt: string;
  exportedAt: string | null;
}

export async function listGapReports(
  token: string,
  product?: string
): Promise<{ success: boolean; data?: GapReport[]; error?: string }> {
  try {
    const params = product ? new URLSearchParams({ product }) : new URLSearchParams();
    const res = await fetch(`${BASE}?${params}`, { headers: authHeader(token) });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}

export async function generateGapReport(
  token: string,
  product?: string,
  version?: string
): Promise<{ success: boolean; data?: GapReport; error?: string }> {
  try {
    const params = new URLSearchParams();
    if (product) params.set('product', product);
    if (version) params.set('version', version);
    const res = await fetch(`${BASE}/generate?${params}`, {
      method: 'POST',
      headers: authHeader(token),
    });
    if (res.status === 204) return { success: true };
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}

export async function exportGapReport(
  id: string,
  token: string
): Promise<void> {
  const res = await fetch(`${BASE}/${id}/export`, { headers: authHeader(token) });
  if (!res.ok) throw new Error(`Export failed: HTTP ${res.status}`);
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `gap-report-${id.substring(0, 8)}.md`;
  a.click();
  URL.revokeObjectURL(url);
}
