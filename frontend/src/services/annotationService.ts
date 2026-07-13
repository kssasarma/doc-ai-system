import { BACKEND_URL } from '../config/backend';
import { ChunkAnnotation } from '../types';

const BASE = BACKEND_URL;

interface ApiResult<T> { success: boolean; data?: T; error?: string; }

export async function fetchAnnotations(
  chunkId: string,
  token: string
): Promise<ApiResult<ChunkAnnotation[]>> {
  try {
    const res = await fetch(`${BASE}/api/chunks/${chunkId}/annotations`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function createAnnotation(
  chunkId: string,
  body: string,
  token: string
): Promise<ApiResult<ChunkAnnotation>> {
  try {
    const res = await fetch(`${BASE}/api/chunks/${chunkId}/annotations`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ body }),
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function deleteAnnotation(
  chunkId: string,
  annotationId: string,
  token: string
): Promise<ApiResult<void>> {
  try {
    const res = await fetch(`${BASE}/api/chunks/${chunkId}/annotations/${annotationId}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}
