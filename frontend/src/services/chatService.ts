import {
  APIResponse,
  BackendChatResponse,
  BackendSessionsResponse,
  SessionsAPIResponse,
  BackendChatHistoryResponse,
  ChatHistoryAPIResponse,
  ReasoningStep,
  Source,
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

// ── Streaming (SSE) ───────────────────────────────────────────────────────────
// EventSource can't do POST + an Authorization header, so this parses the SSE wire format
// (`event: name\ndata: json\n\n`) directly off a streamed fetch() body. Matches
// ChatController#queryStream / ChatService#processQueryStream's event contract.

export interface StreamDonePayload {
  chatId: string;
  messageId: string;
  confidence: number;
  sessionTitle?: string;
  relatedQuestions?: string[];
  reasoningChain?: ReasoningStep[];
}

export interface StreamHandlers {
  onSources: (sources: Source[]) => void;
  onToken: (delta: string) => void;
  onDone: (payload: StreamDonePayload) => void;
}

async function parseSSEStream(response: Response, onEvent: (eventName: string, data: string) => void): Promise<void> {
  const reader = response.body?.getReader();
  if (!reader) throw new Error('Streaming is not supported by this browser/response.');
  const decoder = new TextDecoder();
  let buffer = '';

  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    let sepIndex: number;
    while ((sepIndex = buffer.indexOf('\n\n')) !== -1) {
      const rawEvent = buffer.slice(0, sepIndex);
      buffer = buffer.slice(sepIndex + 2);

      let eventName = 'message';
      const dataLines: string[] = [];
      for (const line of rawEvent.split('\n')) {
        if (line.startsWith('event:')) eventName = line.slice(6).trim();
        else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim());
      }
      if (dataLines.length > 0) onEvent(eventName, dataLines.join('\n'));
    }
  }
}

/** Streams an answer token-by-token. Rejects on network/HTTP failure or a backend `error` event;
 * resolves once the `done` event has been delivered to {@link StreamHandlers.onDone}. Pass
 * `signal` from an AbortController to support "stop generating" — an aborted fetch rejects with
 * `AbortError`, which the caller should treat as a clean stop, not a failure. */
export async function streamChatMessage(
  query: string,
  token: string,
  chatId: string | undefined,
  scope: { product?: string; version?: string } | undefined,
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const requestBody: { question: string; chatId?: string; product?: string; version?: string } = { question: query };
  if (chatId) requestBody.chatId = chatId;
  if (scope?.product) requestBody.product = scope.product;
  if (scope?.version) requestBody.version = scope.version;

  const response = await fetch(`${CHAT_BACKEND_QUERY_URL}/stream`, {
    method: 'POST',
    headers: authHeader(token),
    body: JSON.stringify(requestBody),
    signal,
  });

  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

  await parseSSEStream(response, (eventName, data) => {
    switch (eventName) {
      case 'sources':
        handlers.onSources(JSON.parse(data) as Source[]);
        break;
      case 'token':
        handlers.onToken((JSON.parse(data) as { text: string }).text);
        break;
      case 'done':
        handlers.onDone(JSON.parse(data) as StreamDonePayload);
        break;
      case 'error':
        throw new Error((JSON.parse(data) as { message: string }).message);
      default:
        break;
    }
  });
}
