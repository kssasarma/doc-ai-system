import React, { Suspense, lazy, useState, useCallback, useRef, useEffect } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import { useChatSessions } from './hooks/useChatSessions';
import { useDocumentTitle } from './hooks/useDocumentTitle';
import { createMessage } from './utils/chatUtils';
import { streamChatMessage } from './services/chatService';
import { BackendChatResponse } from './types';
import appConfig from './config/app.json';
import { cn } from './lib/cn';
import { Menu as MenuIcon } from 'lucide-react';
import LoginPage from './components/Auth/LoginPage';
import ChangePasswordPage from './components/Auth/ChangePasswordPage';
import AcceptInvitePage from './components/Auth/AcceptInvitePage';
import ForgotPasswordPage from './components/Auth/ForgotPasswordPage';
import ResetPasswordPage from './components/Auth/ResetPasswordPage';
import Spinner from './components/ui/Spinner';
import AppShell from './components/Layout/AppShell';

const Sidebar = lazy(() => import('./components/Sidebar/Sidebar'));
const ChatArea = lazy(() => import('./components/Chat/ChatArea'));
const HomeScreen = lazy(() => import('./components/Home/HomeScreen'));
const AdminEntry = lazy(() => import('./components/Admin/AdminEntry'));
const BookmarksPage = lazy(() => import('./components/Bookmarks/BookmarksPage'));
const CollectionsPage = lazy(() => import('./components/Collections/CollectionsPage'));
const SharedChatView = lazy(() => import('./components/Chat/SharedChatView'));
const ApiKeysPage = lazy(() => import('./components/Settings/ApiKeysPage'));
const FaqPage = lazy(() => import('./components/Faq/FaqPage'));
const SubscriptionsPage = lazy(() => import('./components/Subscriptions/SubscriptionsPage'));
const LibraryPage = lazy(() => import('./components/Library/LibraryPage'));

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
    isLoadingHistory,
    error: sessionsError,
    createSession,
    deleteSession,
    updateSession,
    updateSessionTitle,
    updateMessage,
    updateMessageByLocalId,
    selectSession,
    addMessage,
    updateSessionChatId,
    refetchSessions,
  } = useChatSessions(token!);

  useDocumentTitle(activeSession?.title);

  const [isLoading, setIsLoading] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [streamingChatId, setStreamingChatId] = useState<string | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);
  // Set by the home screen's "ask anything" composer, which has no session to send into yet —
  // createSession() is async and the new session only becomes `activeSession` on a later render,
  // so the actual send is deferred to the effect below once that session exists.
  const pendingDraftRef = useRef<string | null>(null);

  const handleStopGeneration = useCallback(() => {
    abortControllerRef.current?.abort();
  }, []);

  const handleSendMessage = useCallback(async (content: string, scope?: { product?: string; version?: string }) => {
    if (!activeSessionId || isLoading || !token) return;

    const currentSession = sessions.find(s => s.chatId === activeSessionId);
    if (!currentSession) return;

    addMessage(activeSessionId, createMessage(content, 'user'));
    setIsLoading(true);
    setStreamingChatId(activeSessionId);

    const assistantMessage = createMessage('', 'assistant');
    assistantMessage.isTyping = true;
    addMessage(activeSessionId, assistantMessage);

    const chatIdToSend = currentSession.isPersisted ? activeSessionId : undefined;
    const controller = new AbortController();
    abortControllerRef.current = controller;

    // Reassigned once (in onDone) if the backend created a brand-new session — every callback
    // below closes over this same binding, so later calls automatically target the right session.
    let effectiveChatId = activeSessionId;
    let accumulated = '';
    let flushScheduled = false;
    const flushContent = () => {
      flushScheduled = false;
      updateMessageByLocalId(effectiveChatId, assistantMessage.id, {
        content: accumulated, isTyping: false, isStreaming: true,
      });
    };

    try {
      await streamChatMessage(content, token, chatIdToSend, scope, {
        onSources: (sources) => {
          updateMessageByLocalId(effectiveChatId, assistantMessage.id, {
            sources, isTyping: false, isStreaming: true,
          });
        },
        onToken: (delta) => {
          accumulated += delta;
          // Coalesce rapid deltas into at most one state update per animation frame — a raw
          // per-token setState would repaint far more often than the eye can register and risks
          // jank on long answers.
          if (!flushScheduled) {
            flushScheduled = true;
            requestAnimationFrame(flushContent);
          }
        },
        onDone: (payload) => {
          if (payload.chatId && payload.chatId !== activeSessionId) {
            updateSessionChatId(activeSessionId, payload.chatId);
            effectiveChatId = payload.chatId;
          }
          if (payload.sessionTitle) {
            updateSessionTitle(effectiveChatId, payload.sessionTitle);
          }
          updateMessageByLocalId(effectiveChatId, assistantMessage.id, {
            content: accumulated,
            isTyping: false,
            isStreaming: false,
            messageId: payload.messageId,
            confidence: payload.confidence,
            relatedQuestions: payload.relatedQuestions,
            reasoningChain: payload.reasoningChain,
          });
        },
      }, controller.signal);
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') {
        // User clicked "stop" — keep whatever partial answer already streamed in.
        updateMessageByLocalId(effectiveChatId, assistantMessage.id, {
          content: accumulated, isTyping: false, isStreaming: false,
        });
      } else {
        updateMessageByLocalId(effectiveChatId, assistantMessage.id, {
          content: accumulated || `Error: ${err instanceof Error ? err.message : 'Unknown error occurred'}`,
          isTyping: false,
          isStreaming: false,
        });
      }
    } finally {
      setIsLoading(false);
      setStreamingChatId(null);
      abortControllerRef.current = null;
    }
  }, [activeSessionId, sessions, isLoading, token, addMessage, updateMessageByLocalId,
      updateSessionChatId, updateSessionTitle]);

  const handleRenameSession = useCallback((title: string) => {
    if (!activeSessionId) return;
    updateSession(activeSessionId, { title });
  }, [activeSessionId, updateSession]);

  const handlePinSession = useCallback((chatId: string) => {
    const session = sessions.find(s => s.chatId === chatId);
    if (session) updateSession(chatId, { pinned: !session.pinned });
  }, [sessions, updateSession]);

  const handleRegeneratedAnswer = useCallback((messageId: string, response: BackendChatResponse) => {
    const effectiveChatId = activeSession?.chatId ?? activeSessionId;
    if (!effectiveChatId) return;
    updateMessage(effectiveChatId, messageId, {
      content: response.answer,
      sources: response.sources,
      confidence: response.confidence,
      relatedQuestions: response.relatedQuestions ?? [],
      reasoningChain: response.reasoningChain,
    });
  }, [activeSession, activeSessionId, updateMessage]);

  const handleAsk = useCallback((content: string) => {
    pendingDraftRef.current = content;
    createSession();
  }, [createSession]);

  // Fires once the session created by handleAsk shows up as activeSession (empty — no messages
  // yet) — at that point activeSessionId is populated, so handleSendMessage can actually send.
  useEffect(() => {
    if (pendingDraftRef.current && activeSession && activeSession.messages.length === 0) {
      const draft = pendingDraftRef.current;
      pendingDraftRef.current = null;
      handleSendMessage(draft);
    }
  }, [activeSession, handleSendMessage]);

  const showHomeScreen = !activeSessionId && !isLoadingSessions;

  // A session was selected/created — the drawer's job (letting the reader get to a chat) is done.
  useEffect(() => {
    setMobileSidebarOpen(false);
  }, [activeSessionId]);

  return (
    <div className="flex h-full bg-background overflow-hidden relative">
      <Suspense fallback={<LoadingSpinner />}>
        {/* <768px: the sidebar becomes an overlay drawer instead of a permanent column (Phase 6.6) —
            mirrors AdminLayout's "no room for a fixed sidebar on a phone" mobile treatment. */}
        <div
          className={cn(
            'fixed inset-y-0 left-0 z-40 transition-transform duration-300 ease-out md:relative md:z-auto md:translate-x-0',
            mobileSidebarOpen ? 'translate-x-0' : '-translate-x-full',
          )}
        >
          <Sidebar
            sessions={sessions}
            activeSessionId={activeSessionId}
            onCreateSession={createSession}
            onSelectSession={selectSession}
            onDeleteSession={deleteSession}
            onPinSession={handlePinSession}
            onRenameSession={(chatId, title) => updateSession(chatId, { title })}
            isCollapsed={sidebarCollapsed}
            onToggleCollapse={() => setSidebarCollapsed(prev => !prev)}
            error={sessionsError}
            onRetry={refetchSessions}
          />
        </div>
        {mobileSidebarOpen && (
          <div
            className="fixed inset-0 z-30 bg-black/50 md:hidden"
            onClick={() => setMobileSidebarOpen(false)}
            aria-hidden
          />
        )}

        {/* Mobile-only hamburger to open the drawer — the chat/home surfaces have no header of
            their own to host this, so it floats over whichever is showing. */}
        <button
          onClick={() => setMobileSidebarOpen(true)}
          aria-label="Open chat list"
          className="md:hidden fixed top-3 left-3 z-20 flex items-center justify-center w-9 h-9 rounded-lg bg-surface border border-border shadow-soft text-foreground"
        >
          <MenuIcon size={18} />
        </button>

        {showHomeScreen ? (
          <HomeScreen onAsk={handleAsk} sessions={sessions} onSelectSession={selectSession} />
        ) : (
          <ChatArea
            session={activeSession ?? null}
            onSendMessage={handleSendMessage}
            isLoading={isLoading}
            isStreaming={!!activeSessionId && streamingChatId === activeSessionId}
            onStopGeneration={handleStopGeneration}
            onCreateSession={createSession}
            chatNotFound={!isLoadingSessions && !isLoadingHistory && !!activeSessionId && !activeSession}
            onRenameSession={handleRenameSession}
            onRegeneratedAnswer={handleRegeneratedAnswer}
          />
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
  return <AppShell>{children}</AppShell>;
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
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
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
      <Route path="/library" element={
        <ProtectedRoute>
          <Suspense fallback={<LoadingSpinner />}>
            <LibraryPage />
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
