import React, { Suspense, lazy, useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowLeft, GitFork, User, Bot, AlertTriangle } from 'lucide-react';
import MarkdownContent from './MarkdownContent';
import { SharedChatSession } from '../../types';
import { fetchSharedChat, forkSharedChat } from '../../services/shareService';
import { useAuth } from '../../context/AuthContext';
import { formatTimestamp } from '../../utils/chatUtils';
import { staggerContainer, fadeInUp } from '../../lib/motion';
import { cn } from '../../lib/cn';
import Button from '../ui/Button';
import IconButton from '../ui/IconButton';
import Spinner from '../ui/Spinner';
import EmptyState from '../ui/EmptyState';
import AccountMenu from '../ui/AccountMenu';

const PreferencesModal = lazy(() => import('../Settings/PreferencesModal'));

const SharedChatView: React.FC = () => {
  const { token: shareToken } = useParams<{ token: string }>();
  const { token: authToken, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const [chat, setChat] = useState<SharedChatSession | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isForking, setIsForking] = useState(false);
  const [forked, setForked] = useState(false);
  const [prefsOpen, setPrefsOpen] = useState(false);

  useEffect(() => {
    if (!shareToken) return;
    setIsLoading(true);
    fetchSharedChat(shareToken).then(res => {
      if (res.success && res.data) {
        setChat(res.data);
      } else {
        setError(res.error || 'This share link is invalid or has expired.');
      }
    }).finally(() => setIsLoading(false));
  }, [shareToken]);

  const handleFork = async () => {
    if (!authToken || !shareToken) return;
    setIsForking(true);
    const res = await forkSharedChat(shareToken, authToken);
    if (res.success && res.data) {
      setForked(true);
      setTimeout(() => navigate(`/chat/${res.data!.chatId}`), 1200);
    }
    setIsForking(false);
  };

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center bg-background">
        <Spinner size="lg" />
      </div>
    );
  }

  if (error || !chat) {
    return (
      <div className="flex h-screen items-center justify-center bg-background">
        <EmptyState
          icon={AlertTriangle}
          title={error || 'Chat not found'}
          action={
            <Button variant="link" onClick={() => navigate('/')}>
              Go to home
            </Button>
          }
        />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <div className="bg-surface border-b border-border px-6 py-4 sticky top-0 z-10">
        <div className="max-w-2xl mx-auto flex items-center justify-between gap-4">
          <div className="flex items-center gap-3 min-w-0">
            <IconButton
              label="Go back"
              variant="ghost"
              onClick={() => navigate(-1)}
              className="flex-shrink-0"
            >
              <ArrowLeft size={18} />
            </IconButton>
            <div className="min-w-0">
              <h1 className="text-base font-semibold text-foreground truncate">
                {chat.title || 'Shared conversation'}
              </h1>
              <p className="text-xs text-muted-foreground">
                Shared by {chat.createdByUsername}
                {chat.product && <> · {chat.product} {chat.version}</>}
                {chat.expiresAt && <> · Expires {new Date(chat.expiresAt).toLocaleDateString()}</>}
              </p>
            </div>
          </div>

          {isAuthenticated && (
            <div className="flex items-center gap-2 flex-shrink-0">
              <Button
                onClick={handleFork}
                disabled={isForking || forked}
                leftIcon={<GitFork size={14} />}
              >
                {forked ? 'Forked! Redirecting…' : isForking ? 'Forking…' : 'Continue conversation'}
              </Button>
              <AccountMenu onOpenPreferences={() => setPrefsOpen(true)} compact />
            </div>
          )}
        </div>
      </div>

      {/* Messages */}
      <motion.div
        variants={staggerContainer}
        initial="hidden"
        animate="visible"
        className="max-w-2xl mx-auto px-6 py-8 space-y-6"
      >
        {chat.messages.map((msg, i) => (
          <motion.div
            key={i}
            variants={fadeInUp}
            className={cn('flex gap-3', msg.role === 'USER' ? 'flex-row-reverse' : '')}
          >
            <div className={cn(
              'flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center',
              msg.role === 'USER' ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground',
            )}>
              {msg.role === 'USER' ? <User size={14} /> : <Bot size={14} />}
            </div>
            <div className={cn('flex-1 max-w-[85%] flex flex-col gap-1', msg.role === 'USER' ? 'items-end' : 'items-start')}>
              <div className={cn(
                'px-4 py-3 rounded-2xl text-sm leading-relaxed',
                msg.role === 'USER'
                  ? 'bg-primary text-primary-foreground rounded-tr-sm'
                  : 'bg-surface border border-border text-foreground shadow-soft rounded-tl-sm',
              )}>
                {msg.role === 'ASSISTANT' ? (
                  <div className="prose prose-sm dark:prose-invert max-w-none prose-p:my-1 prose-headings:my-2 prose-pre:rounded prose-pre:p-0 prose-code:text-primary prose-code:bg-muted prose-code:px-1 prose-code:rounded prose-code:before:content-none prose-code:after:content-none [&_pre_code]:bg-transparent [&_pre_code]:text-inherit [&_pre_code]:px-0">
                    <MarkdownContent content={msg.content} />
                  </div>
                ) : (
                  msg.content
                )}
              </div>
              <span className="text-xs text-muted-foreground px-1">
                {formatTimestamp(new Date(msg.createdAt).getTime())}
              </span>
            </div>
          </motion.div>
        ))}

        {!isAuthenticated && (
          <motion.div variants={fadeInUp} className="bg-primary/10 border border-primary/20 rounded-xl p-4 text-center">
            <p className="text-sm text-primary font-medium mb-2">Want to continue this conversation?</p>
            <Button variant="link" onClick={() => navigate('/')}>
              Sign in to fork this chat →
            </Button>
          </motion.div>
        )}
      </motion.div>

      {prefsOpen && (
        <Suspense fallback={null}>
          <PreferencesModal onClose={() => setPrefsOpen(false)} />
        </Suspense>
      )}
    </div>
  );
};

export default SharedChatView;
