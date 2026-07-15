import React, { useState, useRef, useEffect } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Send, Loader2, Square } from 'lucide-react';
import { cn } from '../../lib/cn';
import IconButton from '../ui/IconButton';
import appConfig from '../../config/app.json';

const MAX_MESSAGE_LENGTH = appConfig.ui.chat.maxMessageLength;

interface MessageInputProps {
  onSendMessage: (message: string) => void;
  disabled?: boolean;
  /** True while an answer is actively streaming in — swaps the send button for a stop button. */
  isStreaming?: boolean;
  onStop?: () => void;
  prefillValue?: string;
  /** The active chat's id — drafts are persisted per chat (sessionStorage) so an unsent draft
   * survives a reload and doesn't leak into whichever chat you switch to next (Phase 6.6). Pass
   * undefined to opt out of persistence entirely (no chat to key it against yet). */
  draftKey?: string;
  /** ArrowUp in an empty composer edits the last message (Phase 6.7) — pulls its text back into
   * the composer via the same `prefillValue` mechanism regenerate/related-questions already use. */
  onEditLast?: () => void;
}

function draftStorageKey(draftKey: string) {
  return `docai_draft_${draftKey}`;
}

const MessageInput: React.FC<MessageInputProps> = ({ onSendMessage, disabled, isStreaming, onStop, prefillValue, draftKey, onEditLast }) => {
  const [message, setMessage] = useState(() => (draftKey ? sessionStorage.getItem(draftStorageKey(draftKey)) ?? '' : ''));
  const [focused, setFocused] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const overLimit = message.length > MAX_MESSAGE_LENGTH;

  // Switching chats — load that chat's own draft rather than carrying over whatever was typed
  // in the previous one.
  useEffect(() => {
    setMessage(draftKey ? sessionStorage.getItem(draftStorageKey(draftKey)) ?? '' : '');
  }, [draftKey]);

  useEffect(() => {
    if (!draftKey) return;
    if (message) sessionStorage.setItem(draftStorageKey(draftKey), message);
    else sessionStorage.removeItem(draftStorageKey(draftKey));
  }, [draftKey, message]);

  useEffect(() => {
    if (prefillValue) {
      setMessage(prefillValue);
      textareaRef.current?.focus();
    }
  }, [prefillValue]);

  // "/" focuses the composer from anywhere on the page — skipped while already typing into some
  // other field (this app's own inputs, or a browser-native one) so a literal "/" still types
  // normally there.
  useEffect(() => {
    function handleGlobalKeyDown(e: KeyboardEvent) {
      if (e.key !== '/' || e.metaKey || e.ctrlKey || e.altKey) return;
      const active = document.activeElement;
      const isEditable = active instanceof HTMLElement
        && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA' || active.isContentEditable);
      if (isEditable) return;
      e.preventDefault();
      textareaRef.current?.focus();
    }
    document.addEventListener('keydown', handleGlobalKeyDown);
    return () => document.removeEventListener('keydown', handleGlobalKeyDown);
  }, []);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = message.trim();
    if (trimmed && !disabled && trimmed.length <= MAX_MESSAGE_LENGTH) {
      onSendMessage(trimmed);
      setMessage('');
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e);
    } else if (e.key === 'Escape' && isStreaming && onStop) {
      e.preventDefault();
      onStop();
    } else if (e.key === 'ArrowUp' && !message.trim() && onEditLast) {
      e.preventDefault();
      onEditLast();
    }
  };

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = `${textareaRef.current.scrollHeight}px`;
    }
  }, [message]);

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-1">
      <div className="flex gap-3 items-end">
        <div
          className={cn(
            'flex-1 relative rounded-xl border bg-surface transition-shadow',
            focused ? 'border-primary ring-4 ring-primary/10' : 'border-border',
            overLimit && 'border-danger ring-4 ring-danger/10',
          )}
        >
          <textarea
            ref={textareaRef}
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            onKeyDown={handleKeyDown}
            onFocus={() => setFocused(true)}
            onBlur={() => setFocused(false)}
            placeholder="Ask me anything about your documentation..."
            aria-label="Message"
            className="w-full px-4 py-3 bg-transparent text-sm text-foreground placeholder:text-muted-foreground resize-none focus:outline-none min-h-[48px] max-h-32"
            rows={1}
            disabled={disabled}
            aria-invalid={overLimit}
            aria-describedby={overLimit ? 'message-length-error' : undefined}
          />
        </div>

        {isStreaming ? (
          <IconButton
            type="button"
            label="Stop generating"
            variant="danger"
            size="lg"
            onClick={onStop}
            className="h-[48px] w-[48px] flex-shrink-0"
          >
            <Square size={16} fill="currentColor" />
          </IconButton>
        ) : (
          <IconButton
            type="submit"
            label={disabled ? 'Sending…' : 'Send message'}
            variant="primary"
            size="lg"
            disabled={!message.trim() || disabled || overLimit}
            className="h-[48px] w-[48px] flex-shrink-0"
          >
            <AnimatePresence mode="wait" initial={false}>
              <motion.span
                key={disabled ? 'loading' : 'send'}
                initial={{ opacity: 0, scale: 0.6, rotate: -30 }}
                animate={{ opacity: 1, scale: 1, rotate: 0 }}
                exit={{ opacity: 0, scale: 0.6 }}
                transition={{ duration: 0.15 }}
                className="inline-flex"
              >
                {disabled ? <Loader2 size={19} className="animate-spin" /> : <Send size={19} />}
              </motion.span>
            </AnimatePresence>
          </IconButton>
        )}
      </div>

      {(overLimit || message.length > MAX_MESSAGE_LENGTH * 0.9) && (
        <span
          id="message-length-error"
          className={cn('self-end text-xs pr-1', overLimit ? 'text-danger' : 'text-muted-foreground')}
        >
          {message.length.toLocaleString()} / {MAX_MESSAGE_LENGTH.toLocaleString()}
        </span>
      )}
    </form>
  );
};

export default MessageInput;
