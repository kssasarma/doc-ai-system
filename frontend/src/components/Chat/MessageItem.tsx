import React, { useState } from 'react';
import { User, Bot, Copy, Check } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import { ChatMessage } from '../../types';
import { formatTimestamp } from '../../utils/chatUtils';

interface MessageItemProps {
  message: ChatMessage;
}

const MessageItem: React.FC<MessageItemProps> = ({ message }) => {
  const isUser = message.role === 'user';
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(message.content);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy text:', err);
    }
  };

  return (
    <div className={`flex gap-3 ${isUser ? 'justify-end' : 'justify-start'}`}>
      {!isUser && (
        <div className="w-8 h-8 bg-blue-100 rounded-full flex items-center justify-center flex-shrink-0">
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
              <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"></div>
              <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0.1s' }}></div>
              <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0.2s' }}></div>
            </div>
          )}
        </div>
        <div className={`text-xs text-gray-400 mt-1 ${isUser ? 'text-right' : 'text-left'}`}>
          {formatTimestamp(message.timestamp)}
        </div>
      </div>
      
      {isUser && (
        <div className="w-8 h-8 bg-gray-100 rounded-full flex items-center justify-center flex-shrink-0">
          <User size={16} className="text-gray-600" />
        </div>
      )}
    </div>
  );
};

export default MessageItem;