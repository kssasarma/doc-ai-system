import { INGESTOR_URL } from '../config/backend';
import { ConfluenceSpaceSummary, ConnectorSyncPage, ConnectorSyncStats, ConnectorToken } from '../types';

const BASE = `${INGESTOR_URL}/api/connectors`;

function authHeaders(token: string): Record<string, string> {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

async function unwrap<T>(pending: Promise<Response>): Promise<T> {
  const res = await pending;
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.message ?? `HTTP ${res.status}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

export function listConnectorTokens(token: string): Promise<ConnectorToken[]> {
  return unwrap(fetch(BASE, { headers: authHeaders(token) }));
}

export function deleteConnectorToken(token: string, id: string): Promise<void> {
  return unwrap(fetch(`${BASE}/${id}`, { method: 'DELETE', headers: authHeaders(token) }));
}

// ── Confluence ────────────────────────────────────────────────────────────

export function saveConfluenceToken(
  token: string,
  siteUrl: string,
  accessToken: string,
  refreshToken?: string,
  expiresInSeconds?: number,
): Promise<ConnectorToken> {
  return unwrap(fetch(`${BASE}/confluence/token`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify({ siteUrl, accessToken, refreshToken, expiresInSeconds }),
  }));
}

export function listConfluenceSpaces(token: string): Promise<ConfluenceSpaceSummary[]> {
  return unwrap(fetch(`${BASE}/confluence/spaces`, { headers: authHeaders(token) }));
}

export function syncConfluenceSpace(
  token: string, spaceKey: string, product: string, version: string,
): Promise<{ message: string; tokenId: string }> {
  return unwrap(fetch(`${BASE}/confluence/sync`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify({ spaceKey, product, version }),
  }));
}

export function fetchConfluenceStats(token: string): Promise<ConnectorSyncStats> {
  return unwrap(fetch(`${BASE}/confluence/stats`, { headers: authHeaders(token) }));
}

export function fetchConfluencePages(token: string): Promise<ConnectorSyncPage[]> {
  return unwrap(fetch(`${BASE}/confluence/pages`, { headers: authHeaders(token) }));
}
