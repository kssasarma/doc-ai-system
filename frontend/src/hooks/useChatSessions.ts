import { useState, useCallback, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ChatSession, ChatMessage, BackendSession, BackendHistoryMessage } from '../types';
import { generateId, createNewSession } from '../utils/chatUtils';
import { fetchChatSessions, fetchChatHistory, deleteChatSession, updateChatSession } from '../services/chatService';

function convertBackendSession(backendSession: BackendSession): ChatSession {
  const title = backendSession.title
    ?? (backendSession.product && backendSession.version
      ? `${backendSession.product} ${backendSession.version}`
      : `Chat ${backendSession.chatId.slice(0, 8)}`);

  return {
    chatId: backendSession.chatId,
    title,
    messages: [],
    createdAt: new Date(backendSession.createdAt).getTime(),
    updatedAt: new Date(backendSession.lastActiveAt).getTime(),
    pinned: backendSession.pinned ?? false,
    tags: backendSession.tags ?? [],
    isPersisted: true,
  };
}

function convertBackendMessage(backendMessage: BackendHistoryMessage): ChatMessage {
  return {
    id: backendMessage.id,
    content: backendMessage.content,
    role: backendMessage.role === 'USER' ? 'user' : 'assistant',
    timestamp: new Date(backendMessage.createdAt).getTime(),
  };
}

export function useChatSessions(token: string) {
  const { chatId: urlChatId } = useParams<{ chatId?: string }>();
  const navigate = useNavigate();

  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const activeSessionId = urlChatId || '';

  const loadSessions = useCallback(async () => {
    if (!token) return;
    setIsLoading(true);
    setError(null);
    try {
      const response = await fetchChatSessions(token);
      if (response.success && response.data) {
        setSessions(response.data.sessions.map(convertBackendSession));
      } else {
        setError(response.error || 'Failed to load sessions');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
    } finally {
      setIsLoading(false);
    }
  }, [token]);

  useEffect(() => {
    loadSessions();
  }, [loadSessions]);

  // Load chat history when visiting a direct URL with chatId
  useEffect(() => {
    if (urlChatId && !isLoading) {
      const existingSession = sessions.find(s => s.chatId === urlChatId);
      if (existingSession && existingSession.messages.length === 0) {
        setIsLoadingHistory(true);
        fetchChatHistory(urlChatId, token)
          .then(response => {
            if (response.success && response.data) {
              const messages = response.data.messages.map(convertBackendMessage);
              setSessions(prev => prev.map(s =>
                s.chatId === urlChatId ? { ...s, messages } : s
              ));
            }
          })
          .catch(err => console.error('Failed to load chat history:', err))
          .finally(() => setIsLoadingHistory(false));
      }
    }
  }, [urlChatId, isLoading, sessions, token]);

  const activeSession = sessions.find(session => session.chatId === activeSessionId);

  const createSession = useCallback((backendChatId?: string) => {
    const chatId = typeof backendChatId === 'string' ? backendChatId : undefined;
    const newSession = createNewSession(chatId);
    setSessions(prev => [newSession, ...prev]);
    navigate(`/chat/${newSession.chatId}`);
    return newSession;
  }, [navigate]);

  const deleteSession = useCallback(async (chatId: string) => {
    const response = await deleteChatSession(chatId, token);
    if (!response.success) console.error('Failed to delete session:', response.error);
    setSessions(prev => {
      const filtered = prev.filter(session => session.chatId !== chatId);
      if (chatId === activeSessionId) {
        const newActiveId = filtered.length > 0 ? filtered[0].chatId : '';
        navigate(newActiveId ? `/chat/${newActiveId}` : '/');
      }
      return filtered;
    });
  }, [activeSessionId, navigate, token]);

  const updateSession = useCallback(async (
    chatId: string,
    updates: { title?: string; pinned?: boolean; tags?: string[] }
  ) => {
    // Optimistic update
    setSessions(prev => prev.map(s =>
      s.chatId === chatId ? { ...s, ...updates } : s
    ));
    const response = await updateChatSession(chatId, updates, token);
    if (!response.success) {
      // Rollback on failure - reload from server
      loadSessions();
    }
  }, [token, loadSessions]);

  const addMessage = useCallback((chatId: string, message: ChatMessage) => {
    setSessions(prev => prev.map(session => {
      if (session.chatId !== chatId) return session;
      return {
        ...session,
        messages: [...session.messages, message],
        title: session.messages.length === 0 && message.role === 'user'
          ? message.content.slice(0, 50)
          : session.title,
        updatedAt: Date.now(),
      };
    }));
  }, []);

  const updateSessionTitle = useCallback((chatId: string, title: string) => {
    setSessions(prev => prev.map(s => s.chatId === chatId ? { ...s, title } : s));
  }, []);

  const updateMessage = useCallback((chatId: string, messageId: string, patch: Partial<ChatMessage>) => {
    setSessions(prev => prev.map(session => {
      if (session.chatId !== chatId) return session;
      return {
        ...session,
        messages: session.messages.map(m =>
          m.messageId === messageId ? { ...m, ...patch } : m
        ),
      };
    }));
  }, []);

  const removeTypingIndicators = useCallback((chatId: string) => {
    setSessions(prev => prev.map(session => {
      if (session.chatId !== chatId) return session;
      return {
        ...session,
        messages: session.messages.filter(msg => !msg.isTyping),
        updatedAt: Date.now(),
      };
    }));
  }, []);

  const updateSessionChatId = useCallback((oldChatId: string, newChatId: string) => {
    setSessions(prev => prev.map(session =>
      session.chatId === oldChatId
        ? { ...session, chatId: newChatId, updatedAt: Date.now(), isPersisted: true }
        : session
    ));
    if (activeSessionId === oldChatId) {
      navigate(`/chat/${newChatId}`, { replace: true });
    }
  }, [activeSessionId, navigate]);

  const selectSession = useCallback(async (sessionId: string) => {
    navigate(`/chat/${sessionId}`);
    const session = sessions.find(s => s.chatId === sessionId);
    if (session && session.messages.length > 0) return;

    setIsLoadingHistory(true);
    try {
      const response = await fetchChatHistory(sessionId, token);
      if (response.success && response.data) {
        const messages = response.data.messages.map(convertBackendMessage);
        setSessions(prev => prev.map(s =>
          s.chatId === sessionId ? { ...s, messages } : s
        ));
      }
    } catch (err) {
      console.error('Failed to load chat history:', err);
    } finally {
      setIsLoadingHistory(false);
    }
  }, [navigate, sessions, token]);

  return {
    sessions,
    activeSession,
    activeSessionId,
    isLoading,
    isLoadingHistory,
    error,
    createSession,
    deleteSession,
    updateSession,
    updateSessionTitle,
    updateMessage,
    addMessage,
    removeTypingIndicators,
    updateSessionChatId,
    selectSession,
    refetchSessions: loadSessions,
  };
}
