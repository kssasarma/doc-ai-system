import { BACKEND_URL } from '../config/backend';
import { Collection, CollectionItem } from '../types';

const BASE = BACKEND_URL;

interface ApiResult<T> { success: boolean; data?: T; error?: string; }

export async function fetchCollections(token: string): Promise<ApiResult<Collection[]>> {
  try {
    const res = await fetch(`${BASE}/api/collections`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function createCollection(
  name: string,
  description: string,
  publicAccess: boolean,
  token: string
): Promise<ApiResult<Collection>> {
  try {
    const res = await fetch(`${BASE}/api/collections`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ name, description, publicAccess }),
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function updateCollection(
  id: string,
  updates: Partial<Pick<Collection, 'name' | 'description' | 'publicAccess'>>,
  token: string
): Promise<ApiResult<Collection>> {
  try {
    const res = await fetch(`${BASE}/api/collections/${id}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify(updates),
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function deleteCollection(id: string, token: string): Promise<ApiResult<void>> {
  try {
    const res = await fetch(`${BASE}/api/collections/${id}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function fetchCollectionItems(
  collectionId: string,
  token: string
): Promise<ApiResult<CollectionItem[]>> {
  try {
    const res = await fetch(`${BASE}/api/collections/${collectionId}/items`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function addToCollection(
  collectionId: string,
  chatMessageId: string,
  chatId: string,
  note: string,
  token: string
): Promise<ApiResult<CollectionItem>> {
  try {
    const res = await fetch(`${BASE}/api/collections/${collectionId}/items`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ chatMessageId, chatId, note }),
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function removeFromCollection(
  collectionId: string,
  itemId: string,
  token: string
): Promise<ApiResult<void>> {
  try {
    const res = await fetch(`${BASE}/api/collections/${collectionId}/items/${itemId}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}
