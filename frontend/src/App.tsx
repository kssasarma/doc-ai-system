import React, { Suspense, lazy, useState, useCallback } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import { useChatSessions } from './hooks/useChatSessions';
import { createMessage } from './utils/chatUtils';
import { sendChatMessage } from './services/chatService';
import appConfig from './config/app.json';
import LoginPage from './components/Auth/LoginPage';
import ChangePasswordPage from './components/Auth/ChangePasswordPage';
import AcceptInvitePage from './components/Auth/AcceptInvitePage';
import Spinner from './components/ui/Spinner';

const Sidebar = lazy(() => import('./components/Sidebar/Sidebar'));
const ChatArea = lazy(() => import('./components/Chat/ChatArea'));
const AdminEntry = lazy(() => import('./components/Admin/AdminEntry'));
const BookmarksPage = lazy(() => import('./components/Bookmarks/BookmarksPage'));
const CollectionsPage = lazy(() => import('./components/Collections/CollectionsPage'));
const SharedChatView = lazy(() => import('./components/Chat/SharedChatView'));
const PreferencesModal = lazy(() => import('./components/Settings/PreferencesModal'));
const ApiKeysPage = lazy(() => import('./components/Settings/ApiKeysPage'));
const FaqPage = lazy(() => import('./components/Faq/FaqPage'));
const SubscriptionsPage = lazy(() => import('./components/Subscriptions/SubscriptionsPage'));

const LoadingSpinner = () => (
  <div className="flex h-screen w-full items-center justify-center bg-background">
    <div className="flex flex-col items-center gap-4">
      <Spinner size="lg" />
      <div className="text-lg font-medium text-foreground">Loading {appConfig.app.title}…</div>
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

  const handleSendMessage = useCallback(async (content: string, scope?: { product?: string; version?: string }) => {
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

      const response = await sendChatMessage(content, token, chatIdToSend, scope);

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
          reasoningChain: response.data.reasoningChain,
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
    <div className="flex h-screen bg-background overflow-hidden">
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
  const { isAuthenticated, isLoading, user } = useAuth();
  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center bg-background">
        <Spinner size="lg" />
      </div>
    );
  }
  if (!isAuthenticated) return <LoginPage />;
  if (user?.mustChangePassword) return <Navigate to="/change-password" replace />;
  // SUPER_ADMIN has no tenant (tenantId is always null), so none of the tenant-scoped
  // end-user surfaces here — chat, bookmarks, collections, FAQ, subscriptions, API keys —
  // are meaningful for that role. Keep it confined to the admin console.
  if (user?.role === 'SUPER_ADMIN') return <Navigate to="/admin" replace />;
  return <>{children}</>;
}

function AdminRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isAdmin, isLoading, user } = useAuth();
  if (isLoading) return null;
  if (!isAuthenticated) return <LoginPage />;
  if (user?.mustChangePassword) return <Navigate to="/change-password" replace />;
  if (!isAdmin) return <Navigate to="/" replace />;
  return <>{children}</>;
}

function ChangePasswordRoute() {
  const { isAuthenticated, isLoading, user } = useAuth();
  if (isLoading) return null;
  if (!isAuthenticated) return <LoginPage />;
  // Nothing else links here — this route only exists for the forced first-login reset. Once the
  // flag clears (or for a user who was never required to change it), bounce back into the app
  // instead of leaving them stranded on a form with nothing left to submit.
  if (!user?.mustChangePassword) return <Navigate to="/" replace />;
  return <ChangePasswordPage />;
}

function App() {
  return (
    <Routes>
      <Route path="/change-password" element={<ChangePasswordRoute />} />
      <Route path="/accept-invite" element={<AcceptInvitePage />} />
      <Route path="/admin/*" element={
        <AdminRoute>
          <Suspense fallback={<LoadingSpinner />}>
            <AdminEntry />
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
      <Route path="/collections" element={
        <ProtectedRoute>
          <Suspense fallback={<LoadingSpinner />}>
            <CollectionsPage />
          </Suspense>
        </ProtectedRoute>
      } />
      {/* Public share view — no ProtectedRoute */}
      <Route path="/share/:token" element={
        <Suspense fallback={<LoadingSpinner />}>
          <SharedChatView />
        </Suspense>
      } />
      <Route path="/api-keys" element={
        <ProtectedRoute>
          <Suspense fallback={<LoadingSpinner />}>
            <ApiKeysPage />
          </Suspense>
        </ProtectedRoute>
      } />
      <Route path="/faq" element={
        <ProtectedRoute>
          <Suspense fallback={<LoadingSpinner />}>
            <FaqPage />
          </Suspense>
        </ProtectedRoute>
      } />
      <Route path="/subscriptions" element={
        <ProtectedRoute>
          <Suspense fallback={<LoadingSpinner />}>
            <SubscriptionsPage />
          </Suspense>
        </ProtectedRoute>
      } />
      <Route path="/" element={<ProtectedRoute><ChatPage /></ProtectedRoute>} />
      <Route path="/chat/:chatId" element={<ProtectedRoute><ChatPage /></ProtectedRoute>} />
    </Routes>
  );
}

export default App;
