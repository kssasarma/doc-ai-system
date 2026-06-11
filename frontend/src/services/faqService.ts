const BASE = `${import.meta.env.VITE_BACKEND_URL || 'http://localhost:8082'}/api/faq`;

function authHeader(token: string): Record<string, string> {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

export interface FaqEntry {
  id: string;
  question: string;
  answer: string;
  product: string | null;
  version: string | null;
  sources: string | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  viewCount: number;
  helpfulCount: number;
  createdAt: string;
}

export interface PagedFaqEntries {
  content: FaqEntry[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export async function listApprovedFaq(
  product?: string,
  version?: string,
  page = 0,
  size = 20
): Promise<{ success: boolean; data?: PagedFaqEntries; error?: string }> {
  try {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (product) params.set('product', product);
    if (version) params.set('version', version);
    const res = await fetch(`${BASE}?${params}`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}

export async function markHelpful(
  id: string,
  token: string
): Promise<{ success: boolean; error?: string }> {
  try {
    const res = await fetch(`${BASE}/${id}/helpful`, {
      method: 'POST',
      headers: authHeader(token),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}

// Admin

export async function listPendingFaq(
  token: string,
  page = 0,
  size = 20
): Promise<{ success: boolean; data?: PagedFaqEntries; error?: string }> {
  try {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const res = await fetch(`${BASE}/admin/pending?${params}`, { headers: authHeader(token) });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}

export async function approveFaqEntry(
  id: string,
  token: string
): Promise<{ success: boolean; data?: FaqEntry; error?: string }> {
  try {
    const res = await fetch(`${BASE}/admin/${id}/approve`, {
      method: 'PUT',
      headers: authHeader(token),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}

export async function rejectFaqEntry(
  id: string,
  token: string
): Promise<{ success: boolean; data?: FaqEntry; error?: string }> {
  try {
    const res = await fetch(`${BASE}/admin/${id}/reject`, {
      method: 'PUT',
      headers: authHeader(token),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}

export async function triggerFaqGeneration(
  token: string,
  product: string,
  version?: string
): Promise<{ success: boolean; data?: { entriesGenerated: number; message: string }; error?: string }> {
  try {
    const params = new URLSearchParams({ product });
    if (version) params.set('version', version);
    const res = await fetch(`${BASE}/admin/generate?${params}`, {
      method: 'POST',
      headers: authHeader(token),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}
