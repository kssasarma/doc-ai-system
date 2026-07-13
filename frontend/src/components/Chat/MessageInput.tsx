import React, { useState, useRef, useEffect } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Send, Loader2 } from 'lucide-react';
import { cn } from '../../lib/cn';
import IconButton from '../ui/IconButton';
import appConfig from '../../config/app.json';

const MAX_MESSAGE_LENGTH = appConfig.ui.chat.maxMessageLength;

interface MessageInputProps {
  onSendMessage: (message: string) => void;
  disabled?: boolean;
  prefillValue?: string;
}

const MessageInput: React.FC<MessageInputProps> = ({ onSendMessage, disabled, prefillValue }) => {
  const [message, setMessage] = useState('');
  const [focused, setFocused] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const overLimit = message.length > MAX_MESSAGE_LENGTH;

  useEffect(() => {
    if (prefillValue) {
      setMessage(prefillValue);
      textareaRef.current?.focus();
    }
  }, [prefillValue]);

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
