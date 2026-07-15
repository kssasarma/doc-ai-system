import { INGESTOR_URL } from '../config/backend';
import { DocumentInfo, IngestionStatus, PageResponse } from '../types';

const DOCS_URL = `${INGESTOR_URL}/api/documents`;
const INGEST_URL = `${INGESTOR_URL}/api/ingest`;

function authHeaders(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}` };
}

export async function fetchDocuments(
  token: string, opts?: { q?: string; page?: number; size?: number },
): Promise<PageResponse<DocumentInfo>> {
  const params = new URLSearchParams();
  if (opts?.q) params.set('q', opts.q);
  params.set('page', String(opts?.page ?? 0));
  params.set('size', String(opts?.size ?? 50));
  const res = await fetch(`${DOCS_URL}?${params}`, { headers: authHeaders(token) });
  if (!res.ok) throw new Error(`Failed to fetch documents: ${res.status}`);
  return res.json();
}

export async function deleteDocument(token: string, documentId: string): Promise<void> {
  const res = await fetch(`${DOCS_URL}/${documentId}`, { method: 'DELETE', headers: authHeaders(token) });
  if (!res.ok) throw new Error(`Failed to delete document: ${res.status}`);
}

export async function fetchIngestionStatus(token: string): Promise<IngestionStatus> {
  const res = await fetch(`${INGEST_URL}/status`, { headers: authHeaders(token) });
  if (!res.ok) throw new Error(`Failed to fetch status: ${res.status}`);
  return res.json();
}

export async function uploadDocument(
  token: string,
  file: File,
  product: string,
  version: string,
  documentName?: string
): Promise<DocumentInfo> {
  const form = new FormData();
  form.append('file', file);
  form.append('product', product);
  form.append('version', version);
  if (documentName) form.append('documentName', documentName);

  const res = await fetch(`${DOCS_URL}/upload`, {
    method: 'POST',
    headers: authHeaders(token),
    body: form,
  });

  const data: DocumentInfo = await res.json();
  if (!res.ok) {
    throw new Error(data.error || `Upload failed: ${res.status}`);
  }
  return data;
}

export async function retriggerDocument(token: string, documentId: string): Promise<DocumentInfo> {
  const res = await fetch(`${DOCS_URL}/${documentId}/retrigger`, {
    method: 'POST',
    headers: authHeaders(token),
  });
  const data: DocumentInfo = await res.json();
  if (!res.ok) {
    throw new Error(data.error || `Retrigger failed: ${res.status}`);
  }
  return data;
}

export interface BulkReprocessResult {
  totalFailed: number;
  started: number;
  skipped: string[];
}

export async function reprocessFailedDocuments(token: string): Promise<BulkReprocessResult> {
  const res = await fetch(`${DOCS_URL}/reprocess-failed`, {
    method: 'POST',
    headers: authHeaders(token),
  });
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.error || `Bulk reprocess failed: ${res.status}`);
  }
  return data;
}

/** Only available for documents that still have their original file in storage — always true
 * for a document ingested since Phase 6.3 (files are kept, not deleted, after success); a 409
 * here means either a legacy document from before that change or the file was otherwise removed. */
export async function getDocumentDownloadUrl(token: string, documentId: string): Promise<string> {
  const res = await fetch(`${DOCS_URL}/${documentId}/download-url`, { headers: authHeaders(token) });
  const data = await res.json();
  if (!res.ok || !data.url) {
    throw new Error(data.error || `Could not get download link: ${res.status}`);
  }
  return data.url;
}
