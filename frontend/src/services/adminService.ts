import { INGESTOR_URL } from '../config/backend';
import { DocumentInfo, IngestionStatus } from '../types';

const DOCS_URL = `${INGESTOR_URL}/api/documents`;
const INGEST_URL = `${INGESTOR_URL}/api/ingest`;

function authHeaders(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}` };
}

export async function fetchDocuments(token: string): Promise<DocumentInfo[]> {
  const res = await fetch(DOCS_URL, { headers: authHeaders(token) });
  if (!res.ok) throw new Error(`Failed to fetch documents: ${res.status}`);
  return res.json();
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

/** Only available for documents that still have their original file in storage — completed
 * documents' source files are deleted once ingestion succeeds. */
export async function getDocumentDownloadUrl(token: string, documentId: string): Promise<string> {
  const res = await fetch(`${DOCS_URL}/${documentId}/download-url`, { headers: authHeaders(token) });
  const data = await res.json();
  if (!res.ok || !data.url) {
    throw new Error(data.error || `Could not get download link: ${res.status}`);
  }
  return data.url;
}
