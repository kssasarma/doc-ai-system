import appConfig from '../config/app.json';
import { UpvoteStatus } from '../types';

const BASE = appConfig.api.endpoint;

interface ApiResult<T> { success: boolean; data?: T; error?: string; }

export async function toggleUpvote(messageId: string, token: string): Promise<ApiResult<UpvoteStatus>> {
  try {
    const res = await fetch(`${BASE}/api/chat/messages/${messageId}/upvote`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}
