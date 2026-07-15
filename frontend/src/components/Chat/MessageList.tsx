import React, { useEffect, useRef, useState } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import { motion } from 'framer-motion';
import { ArrowDown, Sparkles } from 'lucide-react';
import { ChatMessage, BackendChatResponse } from '../../types';
import MessageItem from './MessageItem';
import { fadeInUp, scaleIn } from '../../lib/motion';

interface MessageListProps {
  messages: ChatMessage[];
  sessionChatId?: string;
  onRelatedQuestion?: (question: string) => void;
  onRegeneratedAnswer?: (messageId: string, response: BackendChatResponse) => void;
  onEditMessage?: (content: string) => void;
}

// Close enough to the bottom to still count as "following along" — a streaming answer's height
// grows continuously, so an exact ===0 check would drop out of "at bottom" on every token.
const BOTTOM_THRESHOLD_PX = 96;

/**
 * Virtualized (Phase 6.6) — only the messages actually on screen are mounted, so a 300+ message
 * session stays smooth instead of rendering (and diffing) every past message on every render.
 * Message heights vary a lot (markdown, sources panels, reasoning chains), so this uses dynamic
 * measurement (`measureElement`) rather than a fixed row height.
 *
 * Autoscroll only fires while the reader is already at the bottom — previously this force-scrolled
 * on every message-array change (including every streamed token), yanking the viewport out from
 * under anyone who'd scrolled up to reread earlier context.
 */
const MessageList: React.FC<MessageListProps> = ({
  messages,
  sessionChatId,
  onRelatedQuestion,
  onRegeneratedAnswer,
  onEditMessage,
}) => {
  const parentRef = useRef<HTMLDivElement>(null);
  const isAtBottomRef = useRef(true);
  const [showJumpToBottom, setShowJumpToBottom] = useState(false);

  const virtualizer = useVirtualizer({
    count: messages.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 160,
    overscan: 6,
  });

  const scrollToBottom = (behavior: ScrollBehavior = 'auto') => {
    if (messages.length === 0) return;
    virtualizer.scrollToIndex(messages.length - 1, { align: 'end', behavior });
    isAtBottomRef.current = true;
    setShowJumpToBottom(false);
  };

  // Track proximity to bottom so streamed content only auto-follows for a reader who's already there.
  useEffect(() => {
    const el = parentRef.current;
    if (!el) return;
    const handleScroll = () => {
      const distance = el.scrollHeight - el.scrollTop - el.clientHeight;
      const atBottom = distance < BOTTOM_THRESHOLD_PX;
      isAtBottomRef.current = atBottom;
      setShowJumpToBottom(!atBottom && messages.length > 0);
    };
    el.addEventListener('scroll', handleScroll, { passive: true });
    return () => el.removeEventListener('scroll', handleScroll);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [messages.length]);

  // Jump to the newest message immediately (no animation) whenever the conversation itself changes.
  useEffect(() => {
    scrollToBottom('auto');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionChatId]);

  // New message appended, or the last one's content grew (streaming) — only follow if the reader
  // was already at the bottom before this update.
  useEffect(() => {
    if (isAtBottomRef.current) {
      scrollToBottom('auto');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
    <div className="relative h-full">
      <div ref={parentRef} className="h-full overflow-y-auto p-4">
        <div style={{ height: virtualizer.getTotalSize(), width: '100%', position: 'relative' }}>
          {virtualizer.getVirtualItems().map(virtualRow => {
            const message = messages[virtualRow.index];
            return (
              <div
                key={message.id}
                data-index={virtualRow.index}
                ref={virtualizer.measureElement}
                style={{ position: 'absolute', top: 0, left: 0, width: '100%', transform: `translateY(${virtualRow.start}px)` }}
              >
                <motion.div variants={fadeInUp} initial="hidden" animate="visible" className="pb-4">
                  <MessageItem
                    message={message}
                    sessionChatId={sessionChatId}
                    onRelatedQuestion={onRelatedQuestion}
                    onRegeneratedAnswer={onRegeneratedAnswer}
                    onEditMessage={onEditMessage}
                  />
                </motion.div>
              </div>
            );
          })}
        </div>
      </div>

      {showJumpToBottom && (
        <motion.button
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: 8 }}
          onClick={() => scrollToBottom('smooth')}
          className="absolute bottom-4 left-1/2 -translate-x-1/2 flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-surface border border-border shadow-elevated dark:shadow-elevated-dark text-xs font-medium text-foreground hover:bg-surface-hover transition-colors"
        >
          <ArrowDown size={13} /> Jump to latest
        </motion.button>
      )}
    </div>
  );
};

export default MessageList;
