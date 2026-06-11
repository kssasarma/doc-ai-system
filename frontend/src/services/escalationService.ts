import appConfig from '../config/app.json';
import { Escalation } from '../types';

const BASE = appConfig.api.endpoint;

interface ApiResult<T> { success: boolean; data?: T; error?: string; }

export async function fetchEscalations(token: string): Promise<ApiResult<Escalation[]>> {
  try {
    const res = await fetch(`${BASE}/api/escalations`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function createEscalation(
  messageId: string,
  questionText: string,
  aiAnswerText: string | undefined,
  product: string | undefined,
  version: string | undefined,
  token: string
): Promise<ApiResult<Escalation>> {
  try {
    const res = await fetch(`${BASE}/api/escalations/messages/${messageId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ questionText, aiAnswerText, product, version }),
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function answerEscalation(
  id: string,
  expertAnswer: string,
  token: string
): Promise<ApiResult<Escalation>> {
  try {
    const res = await fetch(`${BASE}/api/escalations/${id}/answer`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ expertAnswer }),
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}

export async function updateEscalationStatus(
  id: string,
  status: Escalation['status'],
  token: string
): Promise<ApiResult<Escalation>> {
  try {
    const res = await fetch(`${BASE}/api/escalations/${id}/status`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ status }),
    });
    if (!res.ok) throw new Error(await res.text());
    return { success: true, data: await res.json() };
  } catch (e) {
    return { success: false, error: (e as Error).message };
  }
}
