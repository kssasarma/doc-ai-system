import React, { useEffect, useRef } from 'react';
import { motion } from 'framer-motion';
import { Sparkles } from 'lucide-react';
import { ChatMessage, BackendChatResponse } from '../../types';
import MessageItem from './MessageItem';
import { staggerContainer, scaleIn } from '../../lib/motion';

interface MessageListProps {
  messages: ChatMessage[];
  sessionChatId?: string;
  onRelatedQuestion?: (question: string) => void;
  onRegeneratedAnswer?: (messageId: string, response: BackendChatResponse) => void;
}

const MessageList: React.FC<MessageListProps> = ({
  messages,
  sessionChatId,
  onRelatedQuestion,
  onRegeneratedAnswer,
}) => {
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  if (messages.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center p-8">
        <motion.div variants={scaleIn} initial="hidden" animate="visible" className="text-center">
          <div className="w-14 h-14 rounded-2xl bg-primary/10 flex items-center justify-center mx-auto mb-4">
            <Sparkles size={24} className="text-primary" />
          </div>
          <h3 className="text-lg font-medium text-foreground mb-1">Start your conversation</h3>
          <p className="text-sm text-muted-foreground">Ask me anything about your documentation!</p>
        </motion.div>
      </div>
    );
  }

  return (
    <motion.div
      variants={staggerContainer}
      initial="hidden"
      animate="visible"
      className="h-full overflow-y-auto p-4 space-y-4"
    >
      {messages.map((message) => (
        <MessageItem
          key={message.id}
          message={message}
          sessionChatId={sessionChatId}
          onRelatedQuestion={onRelatedQuestion}
          onRegeneratedAnswer={onRegeneratedAnswer}
        />
      ))}
      <div ref={messagesEndRef} />
    </motion.div>
  );
};

export default MessageList;
