import React, { Suspense, lazy } from 'react';
import { ChatSession } from '../../types';
import { MessageSquarePlus, SearchX } from 'lucide-react';

const MessageList = lazy(() => import('./MessageList'));
const MessageInput = lazy(() => import('./MessageInput'));

interface ChatAreaProps {
  session: ChatSession | null;
  onSendMessage: (message: string) => void;
  isLoading: boolean;
  onCreateSession?: () => void;
  chatNotFound?: boolean;
}

const ChatArea: React.FC<ChatAreaProps> = ({ session, onSendMessage, isLoading, onCreateSession, chatNotFound }) => {
  if (chatNotFound) {
    return (
      <div className="flex-1 flex items-center justify-center bg-gray-50">
        <div className="text-center text-gray-500">
          <SearchX className="w-16 h-16 mx-auto mb-4 text-gray-400" />
          <h2 className="text-2xl font-semibold mb-2 text-gray-700">Chat Not Found</h2>
          <p className="text-lg mb-6">The chat you're looking for doesn't exist or may have been deleted.</p>
          <p className="text-sm mb-6">Select a chat from the sidebar or start a new conversation.</p>
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
      <div className="bg-white border-b border-gray-200 p-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-xl font-semibold text-gray-900">{session.title}</h1>
            <p className="text-sm text-gray-500">{session.messages.length} messages</p>
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
          <MessageList messages={session.messages} />
        </Suspense>
      </div>

      {/* Input */}
      <div className="bg-white border-t border-gray-200 p-4">
        <Suspense fallback={
          <div className="h-12 bg-gray-100 rounded-lg animate-pulse"></div>
        }>
          <MessageInput onSendMessage={onSendMessage} disabled={isLoading} />
        </Suspense>
      </div>
    </div>
  );
};

export default ChatArea;