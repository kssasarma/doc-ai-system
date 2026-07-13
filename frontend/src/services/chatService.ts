import {
  APIResponse,
  BackendChatResponse,
  BackendSessionsResponse,
  SessionsAPIResponse,
  BackendChatHistoryResponse,
  ChatHistoryAPIResponse,
} from '../types';

import { BACKEND_URL } from '../config/backend';

const BACKEND_ROOT = BACKEND_URL;
const CHAT_BACKEND_ROOT_URL = `${BACKEND_ROOT}/api/chat`;
const CHAT_BACKEND_QUERY_URL = `${CHAT_BACKEND_ROOT_URL}/query`;
const CHAT_BACKEND_SESSIONS_URL = `${CHAT_BACKEND_ROOT_URL}/sessions`;
const CHAT_BACKEND_HISTORY_URL = `${CHAT_BACKEND_ROOT_URL}/history`;

function authHeader(token: string): Record<string, string> {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

export async function fetchChatSessions(token: string): Promise<SessionsAPIResponse> {
  try {
    const response = await fetch(CHAT_BACKEND_SESSIONS_URL, { headers: authHeader(token) });
    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
    const data: BackendSessionsResponse = await response.json();
    return { success: true, data };
  } catch (error) {
    return { success: false, error: error instanceof Error ? error.message : 'Unknown error' };
  }
}

export async function fetchChatHistory(chatId: string, token: string): Promise<ChatHistoryAPIResponse> {
  try {
    const response = await fetch(`${CHAT_BACKEND_HISTORY_URL}/${chatId}`, { headers: authHeader(token) });
    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
    const data: BackendChatHistoryResponse = await response.json();
    return { success: true, data };
  } catch (error) {
    return { success: false, error: error instanceof Error ? error.message : 'Unknown error' };
  }
}

export async function deleteChatSession(chatId: string, token: string): Promise<{ success: boolean; error?: string }> {
  try {
    const response = await fetch(`${CHAT_BACKEND_SESSIONS_URL}/${chatId}`, {
      method: 'DELETE',
      headers: authHeader(token),
    });
    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
    return { success: true };
  } catch (error) {
    return { success: false, error: error instanceof Error ? error.message : 'Unknown error' };
  }
}

export async function updateChatSession(
  chatId: string,
  updates: { title?: string; pinned?: boolean; tags?: string[] },
  token: string
): Promise<{ success: boolean; data?: { chatId: string; title?: string; pinned: boolean; tags?: string[] }; error?: string }> {
  try {
    const response = await fetch(`${CHAT_BACKEND_SESSIONS_URL}/${chatId}`, {
      method: 'PATCH',
      headers: authHeader(token),
      body: JSON.stringify(updates),
    });
    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
    const data = await response.json();
    return { success: true, data };
  } catch (error) {
    return { success: false, error: error instanceof Error ? error.message : 'Unknown error' };
  }
}

export async function exportConversation(
  chatId: string,
  format: 'markdown' | 'json',
  token: string
): Promise<void> {
  const response = await fetch(`${CHAT_BACKEND_SESSIONS_URL}/${chatId}/export?format=${format}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) throw new Error(`Export failed: ${response.status}`);
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `chat-${chatId.slice(0, 8)}.${format === 'json' ? 'json' : 'md'}`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

export async function submitFeedback(
  messageId: string,
  rating: 1 | -1,
  token: string,
  feedbackText?: string
): Promise<{ success: boolean; error?: string }> {
  try {
    const response = await fetch(`${CHAT_BACKEND_ROOT_URL}/messages/${messageId}/feedback`, {
      method: 'POST',
      headers: authHeader(token),
      body: JSON.stringify({ rating, feedbackText: feedbackText ?? null }),
    });
    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
    return { success: true };
  } catch (error) {
    return { success: false, error: error instanceof Error ? error.message : 'Unknown error' };
  }
}

export async function regenerateAnswer(
  messageId: string,
  style: 'CONCISE' | 'DETAILED' | 'CODE_FIRST' | 'BALANCED',
  token: string
): Promise<{ success: boolean; data?: BackendChatResponse; error?: string }> {
  try {
    const response = await fetch(`${CHAT_BACKEND_ROOT_URL}/messages/${messageId}/regenerate`, {
      method: 'POST',
      headers: authHeader(token),
      body: JSON.stringify({ style }),
    });
    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
    const data: BackendChatResponse = await response.json();
    return { success: true, data };
  } catch (error) {
    return { success: false, error: error instanceof Error ? error.message : 'Unknown error' };
  }
}

export async function sendChatMessage(
  query: string,
  token: string,
  chatId?: string,
  scope?: { product?: string; version?: string }
): Promise<APIResponse> {
  try {
    const requestBody: { question: string; chatId?: string; product?: string; version?: string } = { question: query };
    if (chatId) requestBody.chatId = chatId;
    if (scope?.product) requestBody.product = scope.product;
    if (scope?.version) requestBody.version = scope.version;

    const response = await fetch(CHAT_BACKEND_QUERY_URL, {
      method: 'POST',
      headers: authHeader(token),
      body: JSON.stringify(requestBody),
    });

    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
    const data: BackendChatResponse = await response.json();
    return { success: true, data };
  } catch (error) {
    return { success: false, error: error instanceof Error ? error.message : 'Unknown error' };
  }
}
