import { BACKEND_URL } from '../config/backend';
import { AppNotification } from '../types';

const BASE = BACKEND_URL;

interface ApiResult<T> { success: boolean; data?: T; error?: string; }

export async function fetchNotifications(token: string): Promise<ApiResult<AppNotification[]>> {
  try {
    const res = await fetch(`${BASE}/api/notifications`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function fetchUnreadCount(token: string): Promise<ApiResult<number>> {
  try {
    const res = await fetch(`${BASE}/api/notifications/unread-count`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error(await res.text());
    const data = await res.json();
    return { success: true, data: data.count };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function markNotificationRead(id: string, token: string): Promise<ApiResult<void>> {
  try {
    const res = await fetch(`${BASE}/api/notifications/${id}/read`, {
      method: 'PATCH',
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function markAllNotificationsRead(token: string): Promise<ApiResult<void>> {
  try {
    const res = await fetch(`${BASE}/api/notifications/read-all`, {
      method: 'PATCH',
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}
