import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, GitFork, User, Bot, AlertTriangle } from 'lucide-react';
import MarkdownContent from './MarkdownContent';
import { SharedChatSession } from '../../types';
import { fetchSharedChat, forkSharedChat } from '../../services/shareService';
import { useAuth } from '../../context/AuthContext';
import { formatTimestamp } from '../../utils/chatUtils';

const SharedChatView: React.FC = () => {
  const { token: shareToken } = useParams<{ token: string }>();
  const { token: authToken, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const [chat, setChat] = useState<SharedChatSession | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isForking, setIsForking] = useState(false);
  const [forked, setForked] = useState(false);

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
      <div className="flex h-screen items-center justify-center">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600" />
      </div>
    );
  }

  if (error || !chat) {
    return (
      <div className="flex h-screen items-center justify-center">
        <div className="text-center space-y-3">
          <AlertTriangle size={40} className="mx-auto text-red-400" />
          <p className="text-lg font-medium text-gray-800">{error || 'Chat not found'}</p>
          <button
            onClick={() => navigate('/')}
            className="text-sm text-blue-600 hover:underline"
          >
            Go to home
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b border-gray-200 px-6 py-4 sticky top-0 z-10">
        <div className="max-w-2xl mx-auto flex items-center justify-between gap-4">
          <div className="flex items-center gap-3 min-w-0">
            <button
              onClick={() => navigate(-1)}
              className="p-2 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-lg transition-colors flex-shrink-0"
            >
              <ArrowLeft size={18} />
            </button>
            <div className="min-w-0">
              <h1 className="text-base font-semibold text-gray-900 truncate">
                {chat.title || 'Shared conversation'}
              </h1>
              <p className="text-xs text-gray-500">
                Shared by {chat.createdByUsername}
                {chat.product && <> · {chat.product} {chat.version}</>}
                {chat.expiresAt && <> · Expires {new Date(chat.expiresAt).toLocaleDateString()}</>}
              </p>
            </div>
          </div>

          {isAuthenticated && (
            <button
              onClick={handleFork}
              disabled={isForking || forked}
              className="flex items-center gap-2 px-4 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-60 transition-colors font-medium flex-shrink-0"
            >
              <GitFork size={14} />
              {forked ? 'Forked! Redirecting…' : isForking ? 'Forking…' : 'Continue conversation'}
            </button>
          )}
        </div>
      </div>

      {/* Messages */}
      <div className="max-w-2xl mx-auto px-6 py-8 space-y-6">
        {chat.messages.map((msg, i) => (
          <div
            key={i}
            className={`flex gap-3 ${msg.role === 'USER' ? 'flex-row-reverse' : ''}`}
          >
            <div className={`flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center ${
              msg.role === 'USER' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600'
            }`}>
              {msg.role === 'USER' ? <User size={14} /> : <Bot size={14} />}
            </div>
            <div className={`flex-1 max-w-[85%] ${msg.role === 'USER' ? 'items-end' : 'items-start'} flex flex-col gap-1`}>
              <div className={`px-4 py-3 rounded-2xl text-sm leading-relaxed ${
                msg.role === 'USER'
                  ? 'bg-blue-600 text-white rounded-tr-sm'
                  : 'bg-white border border-gray-200 text-gray-800 rounded-tl-sm shadow-sm'
              }`}>
                {msg.role === 'ASSISTANT' ? (
                  <div className="prose prose-sm max-w-none prose-p:my-1 prose-headings:my-2 prose-pre:rounded prose-pre:p-0 prose-code:text-blue-600 prose-code:bg-gray-100 prose-code:px-1 prose-code:rounded prose-code:before:content-none prose-code:after:content-none [&_pre_code]:bg-transparent [&_pre_code]:text-inherit [&_pre_code]:px-0">
                    <MarkdownContent content={msg.content} />
                  </div>
                ) : (
                  msg.content
                )}
              </div>
              <span className="text-xs text-gray-400 px-1">
                {formatTimestamp(new Date(msg.createdAt).getTime())}
              </span>
            </div>
          </div>
        ))}

        {!isAuthenticated && (
          <div className="bg-blue-50 border border-blue-100 rounded-xl p-4 text-center">
            <p className="text-sm text-blue-700 font-medium mb-2">Want to continue this conversation?</p>
            <button
              onClick={() => navigate('/')}
              className="text-sm text-blue-600 underline hover:text-blue-800"
            >
              Sign in to fork this chat →
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default SharedChatView;
