import { Bookmark } from '../types';

const BASE = `${import.meta.env.VITE_BACKEND_URL || 'http://localhost:8082'}/api/bookmarks`;

function authHeader(token: string): Record<string, string> {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

export async function fetchBookmarks(token: string): Promise<{ success: boolean; data?: Bookmark[]; error?: string }> {
  try {
    const res = await fetch(BASE, { headers: authHeader(token) });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}

export async function createBookmark(
  token: string,
  chatMessageId: string,
  chatId: string,
  messageExcerpt?: string,
  note?: string,
  tags?: string[]
): Promise<{ success: boolean; data?: Bookmark; error?: string }> {
  try {
    const res = await fetch(BASE, {
      method: 'POST',
      headers: authHeader(token),
      body: JSON.stringify({ chatMessageId, chatId, messageExcerpt, note: note ?? null, tags: tags ?? [] }),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}

export async function deleteBookmark(
  bookmarkId: string,
  token: string
): Promise<{ success: boolean; error?: string }> {
  try {
    const res = await fetch(`${BASE}/${bookmarkId}`, { method: 'DELETE', headers: authHeader(token) });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}

export async function updateBookmark(
  bookmarkId: string,
  token: string,
  note?: string,
  tags?: string[]
): Promise<{ success: boolean; data?: Bookmark; error?: string }> {
  try {
    const res = await fetch(`${BASE}/${bookmarkId}`, {
      method: 'PATCH',
      headers: authHeader(token),
      body: JSON.stringify({ note, tags }),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : 'Unknown error' };
  }
}
