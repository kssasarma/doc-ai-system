import { INGESTOR_URL } from '../config/backend';
import { ChunkReview, PiiFlag } from '../types';

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

/** Approves a quarantined document's PII findings — makes it searchable again. */
export async function releaseQuarantinedDocument(documentId: string, token: string): Promise<ApiResult<void>> {
  try {
    const res = await fetch(`${BASE}/document/${documentId}/release`, {
      method: 'POST',
      headers: authHeaders(token),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}

/** Rejects a quarantined document — deletes it outright. */
export async function rejectQuarantinedDocument(documentId: string, token: string): Promise<ApiResult<void>> {
  try {
    const res = await fetch(`${BASE}/document/${documentId}/reject`, {
      method: 'POST',
      headers: authHeaders(token),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}

/** Excludes a single chunk from search over PII found in it — leaves the rest of the document
 * (and every other chunk) searchable, unlike quarantining/rejecting the whole document. */
export async function excludeChunk(chunkId: string, token: string, reason = 'PII'): Promise<ApiResult<void>> {
  try {
    const res = await fetch(`${BASE}/chunk/${chunkId}/exclude`, {
      method: 'PATCH',
      headers: authHeaders(token),
      body: JSON.stringify({ reason }),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}

/** Reinstates a previously excluded chunk, making it searchable again. */
export async function includeChunk(chunkId: string, token: string): Promise<ApiResult<void>> {
  try {
    const res = await fetch(`${BASE}/chunk/${chunkId}/include`, {
      method: 'PATCH',
      headers: authHeaders(token),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}

/** Lists a document's chunks for PII review, each with any PII flags raised against it. */
export async function fetchDocumentChunks(documentId: string, token: string): Promise<ApiResult<ChunkReview[]>> {
  try {
    const res = await fetch(`${BASE}/document/${documentId}/chunks`, { headers: authHeaders(token) });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}
