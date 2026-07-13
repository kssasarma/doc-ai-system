import { BACKEND_URL } from '../config/backend';
import { ShareLink, ShareRecipient, SharedChatSession } from '../types';

const BASE = BACKEND_URL;

interface ApiResult<T> { success: boolean; data?: T; error?: string; }

export async function createShareLink(
  chatId: string,
  publicAccess: boolean,
  expireDays: number | null,
  token: string
): Promise<ApiResult<ShareLink>> {
  try {
    const res = await fetch(`${BASE}/api/chat/sessions/${chatId}/share`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ publicAccess, expireDays }),
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function updateShareLink(
  chatId: string,
  publicAccess: boolean,
  expireDays: number | null,
  token: string
): Promise<ApiResult<ShareLink>> {
  try {
    const res = await fetch(`${BASE}/api/chat/sessions/${chatId}/share`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ publicAccess, expireDays }),
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function getShareLink(chatId: string, token: string): Promise<ApiResult<ShareLink>> {
  try {
    const res = await fetch(`${BASE}/api/chat/sessions/${chatId}/share`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (res.status === 404) return { success: true, data: undefined };
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function deleteShareLink(chatId: string, token: string): Promise<ApiResult<void>> {
  try {
    const res = await fetch(`${BASE}/api/chat/sessions/${chatId}/share`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function listRecipients(chatId: string, token: string): Promise<ApiResult<ShareRecipient[]>> {
  try {
    const res = await fetch(`${BASE}/api/chat/sessions/${chatId}/share/recipients`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function addRecipient(chatId: string, userId: string, token: string): Promise<ApiResult<ShareRecipient>> {
  try {
    const res = await fetch(`${BASE}/api/chat/sessions/${chatId}/share/recipients`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ userId }),
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function removeRecipient(chatId: string, userId: string, token: string): Promise<ApiResult<void>> {
  try {
    const res = await fetch(`${BASE}/api/chat/sessions/${chatId}/share/recipients/${userId}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function fetchSharedChat(shareToken: string): Promise<ApiResult<SharedChatSession>> {
  try {
    const res = await fetch(`${BASE}/api/share/${shareToken}`);
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function forkSharedChat(
  shareToken: string,
  token: string
): Promise<ApiResult<{ chatId: string }>> {
  try {
    const res = await fetch(`${BASE}/api/share/${shareToken}/fork`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}
