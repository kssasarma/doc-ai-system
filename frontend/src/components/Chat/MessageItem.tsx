import React, { lazy, Suspense, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  User, Bot, Copy, Check, ThumbsUp, ThumbsDown,
  ChevronDown, ChevronUp, Bookmark, BookmarkCheck,
  RefreshCw, ChevronRight, ArrowUp, FolderPlus, HelpCircle,
  MessageSquarePlus, Users, GitBranch,
} from 'lucide-react';
import MarkdownContent from './MarkdownContent';
import { ChatMessage } from '../../types';
import { formatTimestamp } from '../../utils/chatUtils';
import { submitFeedback, regenerateAnswer } from '../../services/chatService';
import { createBookmark } from '../../services/bookmarkService';
import { toggleUpvote } from '../../services/upvoteService';
import { useAuth } from '../../context/AuthContext';
import { fadeInUp, EASE_OUT } from '../../lib/motion';
import { cn } from '../../lib/cn';
import Badge from '../ui/Badge';
import IconButton from '../ui/IconButton';
import Menu from '../ui/Menu';

const AddToCollectionModal = lazy(() => import('./AddToCollectionModal'));
const EscalationModal = lazy(() => import('./EscalationModal'));
const AnnotationsPanel = lazy(() => import('./AnnotationsPanel'));

interface MessageItemProps {
  message: ChatMessage;
  sessionChatId?: string;
  onFeedbackChange?: (messageId: string, rating: 1 | -1) => void;
  onRelatedQuestion?: (question: string) => void;
  onRegeneratedAnswer?: (messageId: string, newAnswer: string, relatedQuestions: string[]) => void;
}

function ConfidenceBadge({ confidence }: { confidence: number }) {
  if (confidence >= 0.8) {
    return <Badge variant="success"><span className="w-1.5 h-1.5 rounded-full bg-success" />High confidence</Badge>;
  }
  if (confidence >= 0.6) {
    return <Badge variant="warning"><span className="w-1.5 h-1.5 rounded-full bg-warning" />Medium confidence</Badge>;
  }
  return <Badge variant="danger"><span className="w-1.5 h-1.5 rounded-full bg-danger" />Low confidence</Badge>;
}

function TypingDots() {
  return (
    <div className="flex gap-1 mt-2">
      {[0, 1, 2].map(i => (
        <motion.div
          key={i}
          className="w-2 h-2 bg-muted-foreground rounded-full"
          animate={{ y: [0, -4, 0] }}
          transition={{ duration: 0.6, repeat: Infinity, delay: i * 0.12, ease: 'easeInOut' }}
        />
      ))}
    </div>
  );
}

const REGEN_OPTIONS = [
  { style: 'BALANCED' as const, label: 'Default' },
  { style: 'CONCISE' as const, label: 'Concise' },
  { style: 'DETAILED' as const, label: 'More detailed' },
  { style: 'CODE_FIRST' as const, label: 'Code first' },
];

const MessageItem: React.FC<MessageItemProps> = ({
  message,
  sessionChatId,
  onFeedbackChange,
  onRelatedQuestion,
  onRegeneratedAnswer,
}) => {
  const isUser = message.role === 'user';
  const { token } = useAuth();

  const [copied, setCopied] = useState(false);
  const [sourcesExpanded, setSourcesExpanded] = useState(false);
  const [expandedAnnotationChunk, setExpandedAnnotationChunk] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<1 | -1 | null>(message.userFeedback ?? null);
  const [feedbackPending, setFeedbackPending] = useState(false);
  const [bookmarked, setBookmarked] = useState(message.isBookmarked ?? false);
  const [bookmarkPending, setBookmarkPending] = useState(false);
  const [upvoteCount, setUpvoteCount] = useState(message.upvoteCount ?? 0);
  const [userUpvoted, setUserUpvoted] = useState(message.userUpvoted ?? false);
  const [upvotePending, setUpvotePending] = useState(false);
  const [regenPending, setRegenPending] = useState(false);
  const [regenStyle, setRegenStyle] = useState<string | null>(null);
  const [showCollectionModal, setShowCollectionModal] = useState(false);
  const [showEscalationModal, setShowEscalationModal] = useState(false);

  const hasSources = !isUser && message.sources && message.sources.length > 0;
  const showConfidence = !isUser && message.confidence !== undefined && !message.isTyping;
  const hasReasoningChain = !isUser && message.reasoningChain && message.reasoningChain.length > 0 && !message.isTyping;
  const [chainExpanded, setChainExpanded] = useState(false);
  const hasRelated = !isUser && message.relatedQuestions && message.relatedQuestions.length > 0 && !message.isTyping;
  const isLowConfidence = (message.confidence ?? 1) < 0.6;
  const isTeamVerified = upvoteCount >= 3;

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(message.content);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch { /* clipboard unavailable */ }
  };

  const handleFeedback = async (rating: 1 | -1) => {
    if (!message.messageId || !token || feedbackPending) return;
    const newRating = feedback === rating ? null : rating;
    setFeedback(newRating);
    if (newRating === null) return;
    setFeedbackPending(true);
    try {
      await submitFeedback(message.messageId, newRating, token);
      onFeedbackChange?.(message.messageId, newRating);
    } catch {
      setFeedback(feedback);
    } finally {
      setFeedbackPending(false);
    }
  };

  const handleBookmark = async () => {
    if (!message.messageId || !sessionChatId || !token || bookmarkPending) return;
    setBookmarkPending(true);
    try {
      const result = await createBookmark(token, message.messageId, sessionChatId, message.content.slice(0, 300));
      if (result.success) setBookmarked(true);
    } catch { /* ignore */ } finally {
      setBookmarkPending(false);
    }
  };

  const handleUpvote = async () => {
    if (!message.messageId || !token || upvotePending) return;
    setUpvotePending(true);
    const prev = { upvoteCount, userUpvoted };
    setUpvoteCount(c => userUpvoted ? c - 1 : c + 1);
    setUserUpvoted(v => !v);
    try {
      const res = await toggleUpvote(message.messageId, token);
      if (res.success && res.data) {
        setUpvoteCount(res.data.upvoteCount);
        setUserUpvoted(res.data.userUpvoted);
      } else {
        setUpvoteCount(prev.upvoteCount);
        setUserUpvoted(prev.userUpvoted);
      }
    } catch {
      setUpvoteCount(prev.upvoteCount);
      setUserUpvoted(prev.userUpvoted);
    } finally {
      setUpvotePending(false);
    }
  };

  const handleRegenerate = async (style: 'CONCISE' | 'DETAILED' | 'CODE_FIRST' | 'BALANCED') => {
    if (!message.messageId || !token || regenPending) return;
    setRegenPending(true);
    setRegenStyle(style);
    try {
      const result = await regenerateAnswer(message.messageId, style, token);
      if (result.success && result.data && onRegeneratedAnswer) {
        onRegeneratedAnswer(message.messageId, result.data.answer, result.data.relatedQuestions ?? []);
      }
    } catch { /* ignore */ } finally {
      setRegenPending(false);
      setRegenStyle(null);
    }
  };

  return (
    <motion.div
      variants={fadeInUp}
      initial="hidden"
      animate="visible"
      className={cn('flex gap-3', isUser ? 'justify-end' : 'justify-start')}
    >
      {!isUser && (
        <div className="w-8 h-8 bg-primary/10 ring-1 ring-primary/15 rounded-full flex items-center justify-center flex-shrink-0 mt-1">
          <Bot size={16} className="text-primary" />
        </div>
      )}

      <div className={cn('max-w-2xl min-w-0', isUser ? 'order-first' : '')}>
        <div
          className={cn(
            'p-4 rounded-2xl relative group',
            isUser
              ? 'bg-primary text-primary-foreground ml-auto rounded-tr-sm'
              : 'bg-surface border border-border text-foreground shadow-soft rounded-tl-sm',
          )}
        >
          {/* Team Verified badge */}
          {!isUser && isTeamVerified && !message.isTyping && (
            <div className="absolute -top-3 left-3">
              <Badge variant="primary"><Users size={10} /> Team Verified</Badge>
            </div>
          )}

          {/* Copy button */}
          <IconButton
            label="Copy to clipboard"
            variant="ghost"
            size="sm"
            onClick={handleCopy}
            className={cn(
              'absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity',
              isUser && 'text-primary-foreground/70 hover:bg-white/15 hover:text-primary-foreground',
            )}
          >
            {copied ? <Check size={14} /> : <Copy size={14} />}
          </IconButton>

          <div className={cn(
            'text-sm leading-relaxed pr-6',
            isUser
              ? ''
              : 'prose prose-sm dark:prose-invert max-w-none prose-p:my-2 prose-pre:rounded-lg prose-pre:p-0 prose-pre:bg-muted prose-pre:border prose-pre:border-border prose-code:text-primary prose-code:bg-muted prose-code:px-1 prose-code:rounded prose-code:before:content-none prose-code:after:content-none [&_pre_code]:bg-transparent [&_pre_code]:text-inherit [&_pre_code]:px-0',
          )}>
            {isUser ? (
              <div className="whitespace-pre-wrap">{message.content}</div>
            ) : (
              <MarkdownContent content={message.content} />
            )}
          </div>

          {regenPending && (
            <div className="flex items-center gap-2 mt-2 text-xs text-muted-foreground">
              <RefreshCw size={12} className="animate-spin" />
              Regenerating ({regenStyle?.toLowerCase()})…
            </div>
          )}

          {message.isTyping && <TypingDots />}
        </div>

        {/* Meta row */}
        {!isUser && !message.isTyping && (
          <div className="flex items-center gap-2 mt-1.5 flex-wrap">
            {showConfidence && <ConfidenceBadge confidence={message.confidence!} />}

            {hasSources && (
              <button
                onClick={() => setSourcesExpanded(v => !v)}
                className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-muted text-muted-foreground hover:bg-surface-hover transition-colors border border-border"
              >
                {message.sources!.length} source{message.sources!.length !== 1 ? 's' : ''}
                {sourcesExpanded ? <ChevronUp size={11} /> : <ChevronDown size={11} />}
              </button>
            )}

            <div className="flex items-center gap-0.5 ml-auto">
              {message.messageId && (
                <IconButton
                  label={userUpvoted ? 'Remove upvote' : 'Upvote — helps verify this answer for the team'}
                  variant="ghost"
                  size="sm"
                  onClick={handleUpvote}
                  disabled={upvotePending}
                  className={cn('gap-1 w-auto px-1.5', userUpvoted && 'text-accent bg-accent/10 hover:bg-accent/15')}
                >
                  <ArrowUp size={12} />
                  {upvoteCount > 0 && <span className="text-xs">{upvoteCount}</span>}
                </IconButton>
              )}

              {message.messageId && sessionChatId && (
                <IconButton label="Add to collection" variant="ghost" size="sm" onClick={() => setShowCollectionModal(true)} className="hover:text-success hover:bg-success/10">
                  <FolderPlus size={13} />
                </IconButton>
              )}

              {message.messageId && sessionChatId && (
                <IconButton
                  label={bookmarked ? 'Bookmarked' : 'Bookmark this answer'}
                  variant="ghost"
                  size="sm"
                  onClick={handleBookmark}
                  disabled={bookmarkPending || bookmarked}
                  className={cn(bookmarked ? 'text-warning' : 'hover:text-warning hover:bg-warning/10')}
                >
                  {bookmarked ? <BookmarkCheck size={13} /> : <Bookmark size={13} />}
                </IconButton>
              )}

              {message.messageId && isLowConfidence && (
                <IconButton label="Ask an expert" variant="ghost" size="sm" onClick={() => setShowEscalationModal(true)} className="hover:text-warning hover:bg-warning/10">
                  <HelpCircle size={13} />
                </IconButton>
              )}

              {message.messageId && (
                <Menu
                  trigger={
                    <IconButton label="Regenerate answer" variant="ghost" size="sm" disabled={regenPending} className="hover:text-primary hover:bg-primary/10">
                      <RefreshCw size={13} className={regenPending ? 'animate-spin' : ''} />
                    </IconButton>
                  }
                  options={REGEN_OPTIONS.map(o => ({
                    key: o.style,
                    label: o.label,
                    onSelect: () => handleRegenerate(o.style),
                  }))}
                />
              )}

              {message.messageId && (
                <>
                  <IconButton
                    label="Helpful"
                    variant="ghost"
                    size="sm"
                    onClick={() => handleFeedback(1)}
                    disabled={feedbackPending}
                    className={cn(feedback === 1 ? 'text-success bg-success/10' : 'hover:text-success hover:bg-success/10')}
                  >
                    <ThumbsUp size={13} />
                  </IconButton>
                  <IconButton
                    label="Not helpful"
                    variant="ghost"
                    size="sm"
                    onClick={() => handleFeedback(-1)}
                    disabled={feedbackPending}
                    className={cn(feedback === -1 ? 'text-danger bg-danger/10' : 'hover:text-danger hover:bg-danger/10')}
                  >
                    <ThumbsDown size={13} />
                  </IconButton>
                </>
              )}
            </div>
          </div>
        )}

        {/* Sources panel */}
        <AnimatePresence initial={false}>
          {hasSources && sourcesExpanded && (
            <motion.div
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              transition={{ duration: 0.22, ease: EASE_OUT }}
              className="overflow-hidden"
            >
              <div className="mt-1.5 border border-border rounded-lg bg-muted divide-y divide-border text-xs">
                {message.sources!.map((src, i) => (
                  <div key={src.chunkId ?? i} className="p-3">
                    <div className="flex items-center justify-between gap-2 mb-1">
                      <span className="font-medium text-foreground truncate">{src.document}</span>
                      <div className="flex items-center gap-1.5 flex-shrink-0">
                        {src.product && (
                          <Badge variant="primary">{src.product}{src.version ? ` ${src.version}` : ''}</Badge>
                        )}
                        {src.relevanceScore !== undefined && (
                          <span className="text-muted-foreground">{Math.round(src.relevanceScore * 100)}%</span>
                        )}
                        {src.chunkId && (
                          <button
                            onClick={() => setExpandedAnnotationChunk(
                              expandedAnnotationChunk === src.chunkId ? null : src.chunkId!
                            )}
                            title="View annotations"
                            className={cn(
                              'flex items-center gap-0.5 px-1.5 py-0.5 rounded transition-colors',
                              expandedAnnotationChunk === src.chunkId
                                ? 'bg-accent/15 text-accent'
                                : 'bg-surface text-muted-foreground hover:bg-accent/10 hover:text-accent',
                            )}
                          >
                            <MessageSquarePlus size={11} />
                            Notes
                          </button>
                        )}
                      </div>
                    </div>
                    {src.excerpt && (
                      <p className="text-muted-foreground leading-relaxed line-clamp-3">{src.excerpt}</p>
                    )}
                    {src.chunkId && expandedAnnotationChunk === src.chunkId && (
                      <Suspense fallback={<div className="mt-2 text-muted-foreground text-xs">Loading annotations…</div>}>
                        <AnnotationsPanel chunkId={src.chunkId} />
                      </Suspense>
                    )}
                  </div>
                ))}
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Multi-hop reasoning chain */}
        {hasReasoningChain && (
          <div className="mt-1.5 border border-accent/20 rounded-lg bg-accent/5">
            <button
              onClick={() => setChainExpanded(v => !v)}
              className="flex items-center gap-2 w-full px-3 py-2 text-xs font-medium text-accent hover:bg-accent/10 rounded-lg transition-colors"
            >
              <GitBranch size={12} />
              Multi-hop reasoning — {message.reasoningChain!.length} search passes
              {chainExpanded ? <ChevronUp size={11} className="ml-auto" /> : <ChevronDown size={11} className="ml-auto" />}
            </button>
            <AnimatePresence initial={false}>
              {chainExpanded && (
                <motion.div
                  initial={{ height: 0, opacity: 0 }}
                  animate={{ height: 'auto', opacity: 1 }}
                  exit={{ height: 0, opacity: 0 }}
                  transition={{ duration: 0.2, ease: EASE_OUT }}
                  className="overflow-hidden"
                >
                  <div className="px-3 pb-2 space-y-1.5">
                    {message.reasoningChain!.map((step, i) => (
                      <div key={i} className="flex items-start gap-2 text-xs">
                        <span className="w-4 h-4 rounded-full bg-accent/20 text-accent flex items-center justify-center flex-shrink-0 text-[10px] font-bold mt-0.5">
                          {i + 1}
                        </span>
                        <div className="flex-1">
                          <p className="text-foreground font-medium">{step.subQuestion}</p>
                          <p className="text-muted-foreground mt-0.5">
                            {step.chunksFound} chunk{step.chunksFound !== 1 ? 's' : ''} found
                            {step.maxSimilarity > 0 && ` · ${Math.round(step.maxSimilarity * 100)}% match`}
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        )}

        {/* Related questions */}
        {hasRelated && (
          <div className="mt-2 bg-primary/5 border border-primary/15 rounded-lg p-3">
            <p className="text-xs font-medium text-primary mb-2">You might also ask:</p>
            <div className="space-y-1">
              {message.relatedQuestions!.map((q, i) => (
                <button
                  key={i}
                  onClick={() => onRelatedQuestion?.(q)}
                  className="flex items-center gap-1.5 w-full text-left text-xs text-primary hover:bg-primary/10 rounded px-2 py-1 transition-colors"
                >
                  <ChevronRight size={11} className="flex-shrink-0" />
                  {q}
                </button>
              ))}
            </div>
          </div>
        )}

        <div className={cn('text-xs text-muted-foreground mt-1', isUser ? 'text-right' : 'text-left')}>
          {formatTimestamp(message.timestamp)}
        </div>
      </div>

      {isUser && (
        <div className="w-8 h-8 bg-muted rounded-full flex items-center justify-center flex-shrink-0 mt-1">
          <User size={16} className="text-muted-foreground" />
        </div>
      )}

      {/* Modals */}
      {showCollectionModal && message.messageId && sessionChatId && (
        <Suspense fallback={null}>
          <AddToCollectionModal
            messageId={message.messageId}
            chatId={sessionChatId}
            onClose={() => setShowCollectionModal(false)}
          />
        </Suspense>
      )}
      {showEscalationModal && message.messageId && (
        <Suspense fallback={null}>
          <EscalationModal
            messageId={message.messageId}
            questionText={message.content}
            aiAnswerText={message.content}
            onClose={() => setShowEscalationModal(false)}
          />
        </Suspense>
      )}
    </motion.div>
  );
};

export default MessageItem;
