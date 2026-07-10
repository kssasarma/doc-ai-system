import React, { lazy, Suspense, useState } from 'react';
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
    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-700 border border-green-200">
        <span className="w-1.5 h-1.5 rounded-full bg-green-500" />
        High confidence
      </span>
    );
  }
  if (confidence >= 0.6) {
    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-700 border border-yellow-200">
        <span className="w-1.5 h-1.5 rounded-full bg-yellow-500" />
        Medium confidence
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-700 border border-red-200">
      <span className="w-1.5 h-1.5 rounded-full bg-red-500" />
      Low confidence
    </span>
  );
}

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
  const [regenMenuOpen, setRegenMenuOpen] = useState(false);
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
    setRegenMenuOpen(false);
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
    <div className={`flex gap-3 ${isUser ? 'justify-end' : 'justify-start'}`}>
      {!isUser && (
        <div className="w-8 h-8 bg-blue-100 rounded-full flex items-center justify-center flex-shrink-0 mt-1">
          <Bot size={16} className="text-blue-600" />
        </div>
      )}

      <div className={`max-w-2xl ${isUser ? 'order-first' : ''}`}>
        <div
          className={`p-4 rounded-lg relative group ${
            isUser
              ? 'bg-blue-600 text-white ml-auto'
              : 'bg-white border border-gray-200 text-gray-900'
          }`}
        >
          {/* Team Verified badge */}
          {!isUser && isTeamVerified && !message.isTyping && (
            <div className="absolute -top-3 left-3">
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-indigo-100 text-indigo-700 border border-indigo-200">
                <Users size={10} /> Team Verified
              </span>
            </div>
          )}

          {/* Copy button */}
          <button
            onClick={handleCopy}
            className={`absolute top-2 right-2 p-1.5 rounded opacity-0 group-hover:opacity-100 transition-opacity ${
              isUser
                ? 'hover:bg-blue-500 text-blue-100'
                : 'hover:bg-gray-100 text-gray-400 hover:text-gray-600'
            }`}
            title="Copy to clipboard"
          >
            {copied ? <Check size={14} /> : <Copy size={14} />}
          </button>

          <div className={`text-sm leading-relaxed pr-6 ${isUser ? '' : 'prose prose-sm max-w-none prose-p:my-2 prose-pre:rounded prose-pre:p-0 prose-code:text-blue-600 prose-code:bg-gray-100 prose-code:px-1 prose-code:rounded prose-code:before:content-none prose-code:after:content-none [&_pre_code]:bg-transparent [&_pre_code]:text-inherit [&_pre_code]:px-0'}`}>
            {isUser ? (
              <div className="whitespace-pre-wrap">{message.content}</div>
            ) : (
              <MarkdownContent content={message.content} />
            )}
          </div>

          {regenPending && (
            <div className="flex items-center gap-2 mt-2 text-xs text-gray-400">
              <RefreshCw size={12} className="animate-spin" />
              Regenerating ({regenStyle?.toLowerCase()})…
            </div>
          )}

          {message.isTyping && (
            <div className="flex gap-1 mt-2">
              <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0.1s' }} />
              <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0.2s' }} />
            </div>
          )}
        </div>

        {/* Meta row */}
        {!isUser && !message.isTyping && (
          <div className="flex items-center gap-2 mt-1.5 flex-wrap">
            {showConfidence && <ConfidenceBadge confidence={message.confidence!} />}

            {hasSources && (
              <button
                onClick={() => setSourcesExpanded(v => !v)}
                className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-600 hover:bg-gray-200 transition-colors border border-gray-200"
              >
                {message.sources!.length} source{message.sources!.length !== 1 ? 's' : ''}
                {sourcesExpanded ? <ChevronUp size={11} /> : <ChevronDown size={11} />}
              </button>
            )}

            <div className="flex items-center gap-1 ml-auto">
              {/* Upvote (community verification) */}
              {message.messageId && (
                <button
                  onClick={handleUpvote}
                  disabled={upvotePending}
                  title={userUpvoted ? 'Remove upvote' : 'Upvote — helps verify this answer for the team'}
                  className={`flex items-center gap-1 px-1.5 py-1 rounded transition-colors text-xs ${
                    userUpvoted
                      ? 'text-indigo-600 bg-indigo-50'
                      : 'text-gray-400 hover:text-indigo-600 hover:bg-indigo-50'
                  }`}
                >
                  <ArrowUp size={12} />
                  {upvoteCount > 0 && <span>{upvoteCount}</span>}
                </button>
              )}

              {/* Add to collection */}
              {message.messageId && sessionChatId && (
                <button
                  onClick={() => setShowCollectionModal(true)}
                  title="Add to collection"
                  className="p-1 rounded text-gray-400 hover:text-green-600 hover:bg-green-50 transition-colors"
                >
                  <FolderPlus size={13} />
                </button>
              )}

              {/* Bookmark */}
              {message.messageId && sessionChatId && (
                <button
                  onClick={handleBookmark}
                  disabled={bookmarkPending || bookmarked}
                  title={bookmarked ? 'Bookmarked' : 'Bookmark this answer'}
                  className={`p-1 rounded transition-colors ${
                    bookmarked
                      ? 'text-yellow-500'
                      : 'text-gray-400 hover:text-yellow-500 hover:bg-yellow-50'
                  }`}
                >
                  {bookmarked ? <BookmarkCheck size={13} /> : <Bookmark size={13} />}
                </button>
              )}

              {/* Escalate (shown when low confidence) */}
              {message.messageId && isLowConfidence && (
                <button
                  onClick={() => setShowEscalationModal(true)}
                  title="Ask an expert"
                  className="p-1 rounded text-gray-400 hover:text-orange-500 hover:bg-orange-50 transition-colors"
                >
                  <HelpCircle size={13} />
                </button>
              )}

              {/* Regenerate */}
              {message.messageId && (
                <div className="relative">
                  <button
                    onClick={() => setRegenMenuOpen(v => !v)}
                    disabled={regenPending}
                    title="Regenerate answer"
                    className="p-1 rounded text-gray-400 hover:text-blue-600 hover:bg-blue-50 transition-colors"
                  >
                    <RefreshCw size={13} className={regenPending ? 'animate-spin' : ''} />
                  </button>
                  {regenMenuOpen && (
                    <div className="absolute right-0 bottom-7 bg-white border border-gray-200 rounded-lg shadow-lg py-1 z-20 w-40">
                      {(['BALANCED', 'CONCISE', 'DETAILED', 'CODE_FIRST'] as const).map(style => (
                        <button
                          key={style}
                          onClick={() => handleRegenerate(style)}
                          className="flex items-center gap-2 w-full px-3 py-1.5 text-xs text-gray-700 hover:bg-gray-50 transition-colors text-left"
                        >
                          {style === 'BALANCED' && 'Default'}
                          {style === 'CONCISE' && 'Concise'}
                          {style === 'DETAILED' && 'More detailed'}
                          {style === 'CODE_FIRST' && 'Code first'}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* Feedback thumbs */}
              {message.messageId && (
                <>
                  <button
                    onClick={() => handleFeedback(1)}
                    disabled={feedbackPending}
                    className={`p-1 rounded transition-colors ${
                      feedback === 1 ? 'text-green-600 bg-green-50' : 'text-gray-400 hover:text-green-600 hover:bg-green-50'
                    }`}
                    title="Helpful"
                  >
                    <ThumbsUp size={13} />
                  </button>
                  <button
                    onClick={() => handleFeedback(-1)}
                    disabled={feedbackPending}
                    className={`p-1 rounded transition-colors ${
                      feedback === -1 ? 'text-red-500 bg-red-50' : 'text-gray-400 hover:text-red-500 hover:bg-red-50'
                    }`}
                    title="Not helpful"
                  >
                    <ThumbsDown size={13} />
                  </button>
                </>
              )}
            </div>
          </div>
        )}

        {/* Sources panel */}
        {hasSources && sourcesExpanded && (
          <div className="mt-1.5 border border-gray-200 rounded-lg bg-gray-50 divide-y divide-gray-100 text-xs">
            {message.sources!.map((src, i) => (
              <div key={src.chunkId ?? i} className="p-3">
                <div className="flex items-center justify-between gap-2 mb-1">
                  <span className="font-medium text-gray-700 truncate">{src.document}</span>
                  <div className="flex items-center gap-1.5 flex-shrink-0">
                    {src.product && (
                      <span className="px-1.5 py-0.5 bg-blue-100 text-blue-700 rounded text-xs">
                        {src.product}{src.version ? ` ${src.version}` : ''}
                      </span>
                    )}
                    {src.relevanceScore !== undefined && (
                      <span className="text-gray-400">{Math.round(src.relevanceScore * 100)}%</span>
                    )}
                    {src.chunkId && (
                      <button
                        onClick={() => setExpandedAnnotationChunk(
                          expandedAnnotationChunk === src.chunkId ? null : src.chunkId!
                        )}
                        title="View annotations"
                        className={`flex items-center gap-0.5 px-1.5 py-0.5 rounded transition-colors ${
                          expandedAnnotationChunk === src.chunkId
                            ? 'bg-purple-100 text-purple-700'
                            : 'bg-gray-100 text-gray-500 hover:bg-purple-50 hover:text-purple-600'
                        }`}
                      >
                        <MessageSquarePlus size={11} />
                        Notes
                      </button>
                    )}
                  </div>
                </div>
                {src.excerpt && (
                  <p className="text-gray-500 leading-relaxed line-clamp-3">{src.excerpt}</p>
                )}
                {src.chunkId && expandedAnnotationChunk === src.chunkId && (
                  <Suspense fallback={<div className="mt-2 text-gray-400 text-xs">Loading annotations…</div>}>
                    <AnnotationsPanel chunkId={src.chunkId} />
                  </Suspense>
                )}
              </div>
            ))}
          </div>
        )}

        {/* Multi-hop reasoning chain */}
        {hasReasoningChain && (
          <div className="mt-1.5 border border-purple-100 rounded-lg bg-purple-50">
            <button
              onClick={() => setChainExpanded(v => !v)}
              className="flex items-center gap-2 w-full px-3 py-2 text-xs font-medium text-purple-700 hover:bg-purple-100 rounded-lg transition-colors"
            >
              <GitBranch size={12} />
              Multi-hop reasoning — {message.reasoningChain!.length} search passes
              {chainExpanded ? <ChevronUp size={11} className="ml-auto" /> : <ChevronDown size={11} className="ml-auto" />}
            </button>
            {chainExpanded && (
              <div className="px-3 pb-2 space-y-1.5">
                {message.reasoningChain!.map((step, i) => (
                  <div key={i} className="flex items-start gap-2 text-xs">
                    <span className="w-4 h-4 rounded-full bg-purple-200 text-purple-700 flex items-center justify-center flex-shrink-0 text-[10px] font-bold mt-0.5">
                      {i + 1}
                    </span>
                    <div className="flex-1">
                      <p className="text-purple-800 font-medium">{step.subQuestion}</p>
                      <p className="text-purple-500 mt-0.5">
                        {step.chunksFound} chunk{step.chunksFound !== 1 ? 's' : ''} found
                        {step.maxSimilarity > 0 && ` · ${Math.round(step.maxSimilarity * 100)}% match`}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Related questions */}
        {hasRelated && (
          <div className="mt-2 bg-blue-50 border border-blue-100 rounded-lg p-3">
            <p className="text-xs font-medium text-blue-700 mb-2">You might also ask:</p>
            <div className="space-y-1">
              {message.relatedQuestions!.map((q, i) => (
                <button
                  key={i}
                  onClick={() => onRelatedQuestion?.(q)}
                  className="flex items-center gap-1.5 w-full text-left text-xs text-blue-700 hover:text-blue-900 hover:bg-blue-100 rounded px-2 py-1 transition-colors"
                >
                  <ChevronRight size={11} className="flex-shrink-0" />
                  {q}
                </button>
              ))}
            </div>
          </div>
        )}

        <div className={`text-xs text-gray-400 mt-1 ${isUser ? 'text-right' : 'text-left'}`}>
          {formatTimestamp(message.timestamp)}
        </div>
      </div>

      {isUser && (
        <div className="w-8 h-8 bg-gray-100 rounded-full flex items-center justify-center flex-shrink-0 mt-1">
          <User size={16} className="text-gray-600" />
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
    </div>
  );
};

export default MessageItem;
