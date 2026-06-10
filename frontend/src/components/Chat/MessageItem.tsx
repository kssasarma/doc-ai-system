import React, { useState } from 'react';
import { User, Bot, Copy, Check, ThumbsUp, ThumbsDown, ChevronDown, ChevronUp } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import { ChatMessage } from '../../types';
import { formatTimestamp } from '../../utils/chatUtils';
import { submitFeedback } from '../../services/chatService';
import { useAuth } from '../../context/AuthContext';

interface MessageItemProps {
  message: ChatMessage;
  onFeedbackChange?: (messageId: string, rating: 1 | -1) => void;
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

const MessageItem: React.FC<MessageItemProps> = ({ message, onFeedbackChange }) => {
  const isUser = message.role === 'user';
  const { token } = useAuth();
  const [copied, setCopied] = useState(false);
  const [sourcesExpanded, setSourcesExpanded] = useState(false);
  const [feedback, setFeedback] = useState<1 | -1 | null>(message.userFeedback ?? null);
  const [feedbackPending, setFeedbackPending] = useState(false);

  const hasSources = !isUser && message.sources && message.sources.length > 0;
  const showConfidence = !isUser && message.confidence !== undefined && !message.isTyping;

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(message.content);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // clipboard not available
    }
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

          <div className={`text-sm leading-relaxed pr-6 ${isUser ? '' : 'prose prose-sm max-w-none prose-p:my-2 prose-pre:bg-gray-100 prose-pre:p-3 prose-pre:rounded prose-code:text-blue-600 prose-code:bg-gray-100 prose-code:px-1 prose-code:rounded prose-code:before:content-none prose-code:after:content-none'}`}>
            {isUser ? (
              <div className="whitespace-pre-wrap">{message.content}</div>
            ) : (
              <ReactMarkdown>{message.content}</ReactMarkdown>
            )}
          </div>

          {message.isTyping && (
            <div className="flex gap-1 mt-2">
              <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0.1s' }} />
              <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0.2s' }} />
            </div>
          )}
        </div>

        {/* Confidence badge + feedback row */}
        {(showConfidence || (hasSources && message.messageId)) && (
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

            {message.messageId && (
              <div className="flex items-center gap-1 ml-auto">
                <button
                  onClick={() => handleFeedback(1)}
                  disabled={feedbackPending}
                  className={`p-1 rounded transition-colors ${
                    feedback === 1
                      ? 'text-green-600 bg-green-50'
                      : 'text-gray-400 hover:text-green-600 hover:bg-green-50'
                  }`}
                  title="Helpful"
                >
                  <ThumbsUp size={13} />
                </button>
                <button
                  onClick={() => handleFeedback(-1)}
                  disabled={feedbackPending}
                  className={`p-1 rounded transition-colors ${
                    feedback === -1
                      ? 'text-red-500 bg-red-50'
                      : 'text-gray-400 hover:text-red-500 hover:bg-red-50'
                  }`}
                  title="Not helpful"
                >
                  <ThumbsDown size={13} />
                </button>
              </div>
            )}
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
                  </div>
                </div>
                {src.excerpt && (
                  <p className="text-gray-500 leading-relaxed line-clamp-3">{src.excerpt}</p>
                )}
              </div>
            ))}
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
    </div>
  );
};

export default MessageItem;
