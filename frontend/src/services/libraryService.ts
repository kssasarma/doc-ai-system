import { BACKEND_URL } from '../config/backend';
import { LibraryDocument } from '../types';

const BASE = `${BACKEND_URL}/api/library`;

/** Every document the caller can actually search — backs the /library page and the home
 * screen's "recently updated" strip (Phase 6.2). Sorted most-recently-updated first. */
export async function fetchLibrary(token: string, q?: string): Promise<LibraryDocument[]> {
  const url = q ? `${BASE}?q=${encodeURIComponent(q)}` : BASE;
  const res = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}
