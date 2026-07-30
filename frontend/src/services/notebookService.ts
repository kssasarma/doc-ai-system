import { INGESTOR_URL } from '../config/backend';
import { Notebook, NotebookDocument } from '../types';

// Personal notebooks live on document-ingestor (same service that owns the tenant-wide admin
// document console) — see NotebookController. Any authenticated user may call these, scoped to
// their own (tenant, ownerId); no ADMIN role required, unlike the rest of that service's API.
const BASE = `${INGESTOR_URL}/api/notebooks`;

interface ApiResult<T> { success: boolean; data?: T; error?: string; }

function authHeaders(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}` };
}

export async function fetchNotebooks(token: string): Promise<ApiResult<Notebook[]>> {
  try {
    const res = await fetch(BASE, { headers: authHeaders(token) });
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function createNotebook(
  name: string, description: string, token: string
): Promise<ApiResult<Notebook>> {
  try {
    const res = await fetch(BASE, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
      body: JSON.stringify({ name, description }),
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function deleteNotebook(id: string, token: string): Promise<ApiResult<void>> {
  try {
    const res = await fetch(`${BASE}/${id}`, { method: 'DELETE', headers: authHeaders(token) });
    if (!res.ok) throw new Error(await res.text());
    return { success: true };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function fetchNotebookDocuments(
  notebookId: string, token: string
): Promise<ApiResult<NotebookDocument[]>> {
  try {
    const res = await fetch(`${BASE}/${notebookId}/documents`, { headers: authHeaders(token) });
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function uploadNotebookDocument(
  notebookId: string, file: File, token: string, documentName?: string
): Promise<ApiResult<NotebookDocument>> {
  try {
    const form = new FormData();
    form.append('file', file);
    if (documentName) form.append('documentName', documentName);

    const res = await fetch(`${BASE}/${notebookId}/documents`, {
      method: 'POST',
      headers: authHeaders(token),
      body: form,
    });
    const data: NotebookDocument = await res.json();
    if (!res.ok) throw new Error(data.error || `Upload failed: ${res.status}`);
    return { success: true, data };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function deleteNotebookDocument(
  notebookId: string, documentId: string, token: string
): Promise<ApiResult<void>> {
  try {
    const res = await fetch(`${BASE}/${notebookId}/documents/${documentId}`, {
      method: 'DELETE',
      headers: authHeaders(token),
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}
