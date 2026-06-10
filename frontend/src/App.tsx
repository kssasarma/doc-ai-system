import React, { Suspense, lazy, useState, useCallback } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import { useChatSessions } from './hooks/useChatSessions';
import { createMessage } from './utils/chatUtils';
import { sendChatMessage } from './services/chatService';
import appConfig from './config/app.json';
import LoginPage from './components/Auth/LoginPage';

const Sidebar = lazy(() => import('./components/Sidebar/Sidebar'));
const ChatArea = lazy(() => import('./components/Chat/ChatArea'));
const AdminPanel = lazy(() => import('./components/Admin/AdminPanel'));
const BookmarksPage = lazy(() => import('./components/Bookmarks/BookmarksPage'));
const PreferencesModal = lazy(() => import('./components/Settings/PreferencesModal'));

const LoadingSpinner = () => (
  <div className="flex h-screen w-full items-center justify-center">
    <div className="flex flex-col items-center gap-4">
      <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600" />
      <div className="text-lg font-medium text-gray-700">Loading {appConfig.app.title}…</div>
    </div>
  </div>
);

function ChatPage() {
  const { token } = useAuth();
  const {
    sessions,
    activeSession,
    activeSessionId,
    isLoading: isLoadingSessions,
    createSession,
    deleteSession,
    updateSession,
    updateSessionTitle,
    updateMessage,
    selectSession,
    addMessage,
    removeTypingIndicators,
    updateSessionChatId,
  } = useChatSessions(token!);

  const [isLoading, setIsLoading] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [prefsOpen, setPrefsOpen] = useState(false);

  const handleSendMessage = useCallback(async (content: string) => {
    if (!activeSessionId || isLoading || !token) return;

    const currentSession = sessions.find(s => s.chatId === activeSessionId);
    if (!currentSession) return;

    addMessage(activeSessionId, createMessage(content, 'user'));
    setIsLoading(true);

    try {
      const typingMessage = createMessage('', 'assistant');
      typingMessage.isTyping = true;
      addMessage(activeSessionId, typingMessage);

      const chatIdToSend = activeSessionId.includes('-') && activeSessionId.length > 40
        ? undefined
        : activeSessionId;

      const response = await sendChatMessage(content, token, chatIdToSend);

      if (response.success && response.data) {
        let effectiveChatId = activeSessionId;
        if (response.data.chatId && response.data.chatId !== activeSessionId) {
          updateSessionChatId(activeSessionId, response.data.chatId);
          effectiveChatId = response.data.chatId;
        }
        removeTypingIndicators(effectiveChatId);

        // Update session title from backend (first-message auto-title)
        if (response.data.sessionTitle) {
          updateSessionTitle(effectiveChatId, response.data.sessionTitle);
        }

        addMessage(effectiveChatId, createMessage(response.data.answer, 'assistant', {
          messageId: response.data.messageId,
          sources: response.data.sources,
          confidence: response.data.confidence,
          relatedQuestions: response.data.relatedQuestions,
        }));
      } else {
        removeTypingIndicators(activeSessionId);
        addMessage(activeSessionId, createMessage(
          `Error: ${response.error || 'Unknown error occurred'}`,
          'assistant'
        ));
      }
    } catch {
      removeTypingIndicators(activeSessionId);
      addMessage(activeSessionId, createMessage(
        'Sorry, I encountered an error. Please try again.',
        'assistant'
      ));
    } finally {
      setIsLoading(false);
    }
  }, [activeSessionId, sessions, isLoading, token, addMessage, removeTypingIndicators,
      updateSessionChatId, updateSessionTitle]);

  const handleRenameSession = useCallback((title: string) => {
    if (!activeSessionId) return;
    updateSession(activeSessionId, { title });
  }, [activeSessionId, updateSession]);

  const handlePinSession = useCallback((chatId: string) => {
    const session = sessions.find(s => s.chatId === chatId);
    if (session) updateSession(chatId, { pinned: !session.pinned });
  }, [sessions, updateSession]);

  const handleRegeneratedAnswer = useCallback((messageId: string, newAnswer: string, relatedQuestions: string[]) => {
    const effectiveChatId = activeSession?.chatId ?? activeSessionId;
    if (!effectiveChatId) return;
    updateMessage(effectiveChatId, messageId, { content: newAnswer, relatedQuestions });
  }, [activeSession, activeSessionId, updateMessage]);

  return (
    <div className="flex h-screen bg-gray-100">
      <Suspense fallback={<LoadingSpinner />}>
        <Sidebar
          sessions={sessions}
          activeSessionId={activeSessionId}
          onCreateSession={createSession}
          onSelectSession={selectSession}
          onDeleteSession={deleteSession}
          onPinSession={handlePinSession}
          onRenameSession={(chatId, title) => updateSession(chatId, { title })}
          onOpenPreferences={() => setPrefsOpen(true)}
          isCollapsed={sidebarCollapsed}
          onToggleCollapse={() => setSidebarCollapsed(prev => !prev)}
        />
        <ChatArea
          session={activeSession ?? null}
          onSendMessage={handleSendMessage}
          isLoading={isLoading}
          onCreateSession={createSession}
          chatNotFound={!isLoadingSessions && !!activeSessionId && !activeSession}
          onRenameSession={handleRenameSession}
          onRegeneratedAnswer={handleRegeneratedAnswer}
        />
        {prefsOpen && (
          <PreferencesModal onClose={() => setPrefsOpen(false)} />
        )}
      </Suspense>
    </div>
  );
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600" />
      </div>
    );
  }
  return isAuthenticated ? <>{children}</> : <LoginPage />;
}

function AdminRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isAdmin, isLoading } = useAuth();
  if (isLoading) return null;
  if (!isAuthenticated) return <LoginPage />;
  if (!isAdmin) return <Navigate to="/" replace />;
  return <>{children}</>;
}

function App() {
  return (
    <Routes>
      <Route path="/admin" element={
        <AdminRoute>
          <Suspense fallback={<LoadingSpinner />}>
            <AdminPanel />
          </Suspense>
        </AdminRoute>
      } />
      <Route path="/bookmarks" element={
        <ProtectedRoute>
          <Suspense fallback={<LoadingSpinner />}>
            <BookmarksPage />
          </Suspense>
        </ProtectedRoute>
      } />
      <Route path="/" element={<ProtectedRoute><ChatPage /></ProtectedRoute>} />
      <Route path="/chat/:chatId" element={<ProtectedRoute><ChatPage /></ProtectedRoute>} />
    </Routes>
  );
}

export default App;
