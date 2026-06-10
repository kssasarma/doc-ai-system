import { APIResponse, BackendChatResponse, BackendSessionsResponse, SessionsAPIResponse, BackendChatHistoryResponse, ChatHistoryAPIResponse } from '../types';

const CHAT_BACKEND_ROOT_URL = `${import.meta.env.VITE_BACKEND_URL || 'http://localhost:8082'}/api/chat`;
const CHAT_BACKEND_QUERY_URL = `${CHAT_BACKEND_ROOT_URL}/query`;
const CHAT_BACKEND_SESSIONS_URL = `${CHAT_BACKEND_ROOT_URL}/sessions`;
const CHAT_BACKEND_HISTORY_URL = `${CHAT_BACKEND_ROOT_URL}/history`;

function authHeader(token: string): Record<string, string> {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

export async function fetchChatSessions(token: string): Promise<SessionsAPIResponse> {
  try {
    const response = await fetch(CHAT_BACKEND_SESSIONS_URL, {
      headers: authHeader(token),
    });
    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
    const data: BackendSessionsResponse = await response.json();
    return { success: true, data };
  } catch (error) {
    return { success: false, error: error instanceof Error ? error.message : 'Unknown error' };
  }
}

export async function fetchChatHistory(chatId: string, token: string): Promise<ChatHistoryAPIResponse> {
  try {
    const response = await fetch(`${CHAT_BACKEND_HISTORY_URL}/${chatId}`, {
      headers: authHeader(token),
    });
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

export async function sendChatMessage(query: string, token: string, chatId?: string): Promise<APIResponse> {
  try {
    const requestBody: { question: string; chatId?: string } = { question: query };
    if (chatId) requestBody.chatId = chatId;

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
