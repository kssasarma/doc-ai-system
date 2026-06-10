import React, { Suspense, lazy, useState, useCallback } from 'react';
import { ChatSession, ChatMessage } from '../../types';
import { MessageSquarePlus, SearchX, Download, Pencil, Check, X } from 'lucide-react';
import { exportConversation } from '../../services/chatService';
import { useAuth } from '../../context/AuthContext';

const MessageList = lazy(() => import('./MessageList'));
const MessageInput = lazy(() => import('./MessageInput'));

interface ChatAreaProps {
  session: ChatSession | null;
  onSendMessage: (message: string) => void;
  isLoading: boolean;
  onCreateSession?: () => void;
  chatNotFound?: boolean;
  onRenameSession?: (title: string) => void;
  onRegeneratedAnswer?: (messageId: string, newAnswer: string, relatedQuestions: string[]) => void;
}

const ChatArea: React.FC<ChatAreaProps> = ({
  session,
  onSendMessage,
  isLoading,
  onCreateSession,
  chatNotFound,
  onRenameSession,
  onRegeneratedAnswer,
}) => {
  const { token } = useAuth();
  const [editingTitle, setEditingTitle] = useState(false);
  const [titleValue, setTitleValue] = useState('');
  const [exportPending, setExportPending] = useState(false);
  const [prefillQuestion, setPrefillQuestion] = useState<string | undefined>();

  const handleStartEditTitle = () => {
    setTitleValue(session?.title ?? '');
    setEditingTitle(true);
  };

  const handleCommitTitle = () => {
    const trimmed = titleValue.trim();
    if (trimmed && onRenameSession) onRenameSession(trimmed);
    setEditingTitle(false);
  };

  const handleCancelTitle = () => setEditingTitle(false);

  const handleExport = async (format: 'markdown' | 'json') => {
    if (!session || !token) return;
    setExportPending(true);
    try {
      await exportConversation(session.chatId, format, token);
    } catch (e) {
      console.error('Export failed', e);
    } finally {
      setExportPending(false);
    }
  };

  const handleRelatedQuestion = useCallback((question: string) => {
    setPrefillQuestion(question);
  }, []);

  const handleSend = useCallback((msg: string) => {
    setPrefillQuestion(undefined);
    onSendMessage(msg);
  }, [onSendMessage]);

  if (chatNotFound) {
    return (
      <div className="flex-1 flex items-center justify-center bg-gray-50">
        <div className="text-center text-gray-500">
          <SearchX className="w-16 h-16 mx-auto mb-4 text-gray-400" />
          <h2 className="text-2xl font-semibold mb-2 text-gray-700">Chat Not Found</h2>
          <p className="text-lg mb-6">The chat you're looking for doesn't exist or may have been deleted.</p>
          {onCreateSession && (
            <button
              onClick={onCreateSession}
              className="inline-flex items-center gap-2 px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium"
            >
              <MessageSquarePlus className="w-5 h-5" />
              Start New Chat
            </button>
          )}
        </div>
      </div>
    );
  }

  if (!session) {
    return (
      <div className="flex-1 flex items-center justify-center bg-gray-50">
        <div className="text-center text-gray-500">
          <div className="text-6xl mb-4">💬</div>
          <h2 className="text-2xl font-semibold mb-2">Welcome to Docs-inator</h2>
          <p className="text-lg mb-6">Select a chat from the sidebar or start a new conversation</p>
          {onCreateSession && (
            <button
              onClick={onCreateSession}
              className="inline-flex items-center gap-2 px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium"
            >
              <MessageSquarePlus className="w-5 h-5" />
              Start New Chat
            </button>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 flex flex-col bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b border-gray-200 px-4 py-3">
        <div className="flex items-center justify-between gap-2">
          <div className="flex-1 min-w-0">
            {editingTitle ? (
              <div className="flex items-center gap-2">
                <input
                  autoFocus
                  value={titleValue}
                  onChange={e => setTitleValue(e.target.value)}
                  onKeyDown={e => {
                    if (e.key === 'Enter') handleCommitTitle();
                    else if (e.key === 'Escape') handleCancelTitle();
                  }}
                  className="text-lg font-semibold text-gray-900 bg-gray-100 border border-blue-400 rounded px-2 py-0.5 focus:outline-none flex-1 min-w-0"
                />
                <button onClick={handleCommitTitle} className="text-green-600 hover:text-green-700">
                  <Check size={16} />
                </button>
                <button onClick={handleCancelTitle} className="text-gray-400 hover:text-gray-600">
                  <X size={16} />
                </button>
              </div>
            ) : (
              <div className="flex items-center gap-2 group">
                <h1 className="text-xl font-semibold text-gray-900 truncate">{session.title}</h1>
                {onRenameSession && (
                  <button
                    onClick={handleStartEditTitle}
                    className="opacity-0 group-hover:opacity-100 p-1 text-gray-400 hover:text-gray-600 rounded transition-all"
                    title="Rename session"
                  >
                    <Pencil size={13} />
                  </button>
                )}
              </div>
            )}
            <p className="text-sm text-gray-500">{session.messages.length} messages</p>
          </div>

          {/* Export menu */}
          <div className="relative group/export flex-shrink-0">
            <button
              disabled={exportPending}
              className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded-lg transition-colors border border-gray-200"
              title="Export conversation"
            >
              <Download size={14} className={exportPending ? 'animate-bounce' : ''} />
              Export
            </button>
            <div className="absolute right-0 top-full mt-1 bg-white border border-gray-200 rounded-lg shadow-lg py-1 z-10 hidden group-hover/export:block w-36">
              <button
                onClick={() => handleExport('markdown')}
                className="w-full text-left px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50 transition-colors"
              >
                Markdown (.md)
              </button>
              <button
                onClick={() => handleExport('json')}
                className="w-full text-left px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50 transition-colors"
              >
                JSON (.json)
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-hidden">
        <Suspense fallback={
          <div className="flex-1 flex items-center justify-center">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
          </div>
        }>
          <MessageList
            messages={session.messages}
            sessionChatId={session.chatId}
            onRelatedQuestion={handleRelatedQuestion}
            onRegeneratedAnswer={onRegeneratedAnswer}
          />
        </Suspense>
      </div>

      {/* Input */}
      <div className="bg-white border-t border-gray-200 p-4">
        <Suspense fallback={<div className="h-12 bg-gray-100 rounded-lg animate-pulse"></div>}>
          <MessageInput
            onSendMessage={handleSend}
            disabled={isLoading}
            prefillValue={prefillQuestion}
          />
        </Suspense>
      </div>
    </div>
  );
};

export default ChatArea;
