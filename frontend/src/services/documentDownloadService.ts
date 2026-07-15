import axios from 'axios';
import { BACKEND_URL } from '../config/backend';

interface DownloadUrlResponse {
  url?: string;
  expiresInSeconds?: number;
  error?: string;
}

/** Backs "open citation" (Phase 6.3) — resolves a short-lived presigned URL for a document's
 * original file, gated by the caller's own document access (see DocumentDownloadController). */
export async function fetchDocumentDownloadUrl(token: string, documentId: string): Promise<string> {
  const { data } = await axios.get<DownloadUrlResponse>(
    `${BACKEND_URL}/api/documents/${documentId}/download-url`,
    { headers: { Authorization: `Bearer ${token}` }, validateStatus: () => true },
  );
  if (!data.url) {
    throw new Error(data.error ?? 'Could not open this document');
  }
  return data.url;
}
