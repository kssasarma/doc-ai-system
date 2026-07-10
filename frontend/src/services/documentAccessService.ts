import axios from 'axios';
import type { DocumentGrantee, DocumentGroupGrantee } from '../types';

const BOT_URL = import.meta.env.VITE_BOT_API_URL ?? 'http://localhost:8082';

function headers(token: string) {
  return { Authorization: `Bearer ${token}` };
}

export async function listGrantees(token: string, documentId: string): Promise<DocumentGrantee[]> {
  const { data } = await axios.get<DocumentGrantee[]>(
    `${BOT_URL}/api/documents/${documentId}/access`,
    { headers: headers(token) },
  );
  return data;
}

export async function grantDocumentAccess(token: string, documentId: string, userId: string): Promise<DocumentGrantee> {
  const { data } = await axios.post<DocumentGrantee>(
    `${BOT_URL}/api/documents/${documentId}/access`,
    { userId },
    { headers: headers(token) },
  );
  return data;
}

export async function revokeDocumentAccess(token: string, documentId: string, userId: string): Promise<void> {
  await axios.delete(`${BOT_URL}/api/documents/${documentId}/access/${userId}`, { headers: headers(token) });
}

// Group grants (Phase 8) — same resource, grants every current/future member of the group access.
export async function listGroupGrantees(token: string, documentId: string): Promise<DocumentGroupGrantee[]> {
  const { data } = await axios.get<DocumentGroupGrantee[]>(
    `${BOT_URL}/api/documents/${documentId}/access/groups`,
    { headers: headers(token) },
  );
  return data;
}

export async function grantDocumentAccessToGroup(token: string, documentId: string, groupId: string): Promise<DocumentGroupGrantee> {
  const { data } = await axios.post<DocumentGroupGrantee>(
    `${BOT_URL}/api/documents/${documentId}/access/groups`,
    { groupId },
    { headers: headers(token) },
  );
  return data;
}

export async function revokeDocumentAccessFromGroup(token: string, documentId: string, groupId: string): Promise<void> {
  await axios.delete(`${BOT_URL}/api/documents/${documentId}/access/groups/${groupId}`, { headers: headers(token) });
}
