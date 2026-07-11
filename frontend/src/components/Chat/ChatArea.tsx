import React, { Suspense, lazy, useState, useCallback, useEffect } from 'react';
import { motion } from 'framer-motion';
import { ChatSession } from '../../types';
import { MessageSquarePlus, SearchX, Download, Pencil, Check, X, Share2, MessageCircle } from 'lucide-react';
import { exportConversation } from '../../services/chatService';
import { useAuth } from '../../context/AuthContext';
import ScopeChip from './ScopeChip';
import { fadeIn } from '../../lib/motion';
import Button from '../ui/Button';
import IconButton from '../ui/IconButton';
import Menu from '../ui/Menu';
import EmptyState from '../ui/EmptyState';
import { Skeleton } from '../ui/Skeleton';

const ShareModal = lazy(() => import('./ShareModal'));

const MessageList = lazy(() => import('./MessageList'));
const MessageInput = lazy(() => import('./MessageInput'));

export interface ChatScope {
  product?: string;
  version?: string;
}

interface ChatAreaProps {
  session: ChatSession | null;
  onSendMessage: (message: string, scope?: ChatScope) => void;
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
  const [shareOpen, setShareOpen] = useState(false);
  const [scopeProduct, setScopeProduct] = useState<string | undefined>();
  const [scopeVersion, setScopeVersion] = useState<string | undefined>();

  // Scope is per-conversation, not global — switching to a different (or new) chat clears any pin.
  useEffect(() => {
    setScopeProduct(undefined);
    setScopeVersion(undefined);
  }, [session?.chatId]);

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
    onSendMessage(msg, { product: scopeProduct, version: scopeVersion });
  }, [onSendMessage, scopeProduct, scopeVersion]);

  if (chatNotFound) {
    return (
      <div className="flex-1 flex items-center justify-center bg-background">
        <EmptyState
          icon={SearchX}
          title="Chat Not Found"
          description="The chat you're looking for doesn't exist or may have been deleted."
          action={onCreateSession && (
            <Button onClick={onCreateSession} leftIcon={<MessageSquarePlus size={16} />}>
              Start New Chat
            </Button>
          )}
        />
      </div>
    );
  }

  if (!session) {
    return (
      <div className="flex-1 flex items-center justify-center bg-background">
        <EmptyState
          icon={MessageCircle}
          title="Welcome to Docs-inator"
          description="Select a chat from the sidebar or start a new conversation."
          action={onCreateSession && (
            <Button onClick={onCreateSession} leftIcon={<MessageSquarePlus size={16} />}>
              Start New Chat
            </Button>
          )}
        />
      </div>
    );
  }

  return (
    <div className="flex-1 flex flex-col bg-background min-w-0">
      {/* Header */}
      <motion.div variants={fadeIn} initial="hidden" animate="visible" className="bg-surface border-b border-border px-4 py-3">
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
                  className="text-lg font-semibold text-foreground bg-muted border border-primary rounded px-2 py-0.5 focus:outline-none flex-1 min-w-0"
                />
                <IconButton label="Save title" variant="ghost" size="sm" onClick={handleCommitTitle} className="text-success hover:bg-success/10">
                  <Check size={16} />
                </IconButton>
                <IconButton label="Cancel rename" variant="ghost" size="sm" onClick={handleCancelTitle}>
                  <X size={16} />
                </IconButton>
              </div>
            ) : (
              <div className="flex items-center gap-2 group">
                <h1 className="text-xl font-semibold text-foreground truncate">{session.title}</h1>
                {onRenameSession && (
                  <IconButton
                    label="Rename session"
                    variant="ghost"
                    size="sm"
                    onClick={handleStartEditTitle}
                    className="opacity-0 group-hover:opacity-100"
                  >
                    <Pencil size={13} />
                  </IconButton>
                )}
              </div>
            )}
            <p className="text-sm text-muted-foreground">{session.messages.length} messages</p>
          </div>

          {/* Retrieval scope (optional, per-conversation) */}
          <ScopeChip
            product={scopeProduct}
            version={scopeVersion}
            onChange={(p, v) => { setScopeProduct(p); setScopeVersion(v); }}
          />

          <Button variant="outline" size="sm" onClick={() => setShareOpen(true)} leftIcon={<Share2 size={14} />} className="flex-shrink-0">
            Share
          </Button>

          <Menu
            align="end"
            trigger={
              <Button variant="outline" size="sm" disabled={exportPending} leftIcon={<Download size={14} className={exportPending ? 'animate-bounce' : ''} />}>
                Export
              </Button>
            }
            options={[
              { key: 'markdown', label: 'Markdown (.md)', onSelect: () => handleExport('markdown') },
              { key: 'json', label: 'JSON (.json)', onSelect: () => handleExport('json') },
            ]}
          />
        </div>
      </motion.div>

      {/* Messages */}
      <div className="flex-1 overflow-hidden">
        <Suspense fallback={
          <div className="p-4 space-y-4">
            <Skeleton className="h-16 w-2/3" />
            <Skeleton className="h-16 w-1/2 ml-auto" />
            <Skeleton className="h-24 w-3/4" />
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
      <div className="bg-surface border-t border-border p-4">
        <Suspense fallback={<Skeleton className="h-12 rounded-xl" />}>
          <MessageInput
            onSendMessage={handleSend}
            disabled={isLoading}
            prefillValue={prefillQuestion}
          />
        </Suspense>
      </div>

      {/* Share modal */}
      {shareOpen && (
        <Suspense fallback={null}>
          <ShareModal chatId={session.chatId} onClose={() => setShareOpen(false)} />
        </Suspense>
      )}
    </div>
  );
};

export default ChatArea;
