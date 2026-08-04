import React, { useEffect, useRef, useState } from 'react';
import { Send, Sparkles } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { sendChatMessage } from '../../services/chatService';
import { Source } from '../../types';
import { cn } from '../../lib/cn';
import Button from '../ui/Button';
import { Card } from '../ui/Card';
import EmptyState from '../ui/EmptyState';
import Spinner from '../ui/Spinner';

interface PanelMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  sources?: Source[];
  confidence?: number;
}

/**
 * A self-contained ask-and-answer panel scoped to one notebook — every question here is answered
 * only from that notebook's own documents (see ChatService#processQuery's notebookId branch),
 * never the tenant-wide corpus. Deliberately its own lightweight, non-streaming conversation
 * rather than being wired into the main sidebar/session chat experience (ChatPage/useChatSessions):
 * a notebook is a scratch space for questions against a specific set of sources, not a session a
 * user manages/pins/renames alongside their regular chats.
 */
export default function NotebookChatPanel({ notebookId, hasCompletedDocuments }: {
  notebookId: string;
  hasCompletedDocuments: boolean;
}) {
  const { token } = useAuth();
  const [messages, setMessages] = useState<PanelMessage[]>([]);
  const [question, setQuestion] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const chatIdRef = useRef<string | undefined>(undefined);
  const bottomRef = useRef<HTMLDivElement>(null);

  // Starting fresh with a different notebook must not carry over the previous notebook's
  // conversation or backend session id.
  useEffect(() => {
    setMessages([]);
    chatIdRef.current = undefined;
    setError(null);
  }, [notebookId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = async () => {
    const q = question.trim();
    if (!q || !token || isSending) return;

    setMessages(prev => [...prev, { id: `local-${Date.now()}`, role: 'user', content: q }]);
    setQuestion('');
    setIsSending(true);
    setError(null);

    const res = await sendChatMessage(q, token, chatIdRef.current, { notebookId });
    if (res.success && res.data) {
      chatIdRef.current = res.data.chatId;
      setMessages(prev => [...prev, {
        id: res.data!.messageId,
        role: 'assistant',
        content: res.data!.answer,
        sources: res.data!.sources,
        confidence: res.data!.confidence,
      }]);
    } else {
      setError(res.error ?? 'Failed to get an answer');
    }
    setIsSending(false);
  };

  return (
    <div className="flex flex-col h-full">
      <div className="flex-1 overflow-y-auto space-y-4 mb-3 min-h-[16rem]">
        {messages.length === 0 ? (
          <EmptyState
            icon={Sparkles}
            title={hasCompletedDocuments ? 'Ask this notebook anything' : 'Upload a document first'}
            description={hasCompletedDocuments
              ? 'Answers are grounded only in the documents you\'ve uploaded to this notebook.'
              : 'Once a document finishes processing, you can ask questions about it here.'}
          />
        ) : (
          messages.map(m => (
            <div key={m.id} className={cn('flex', m.role === 'user' ? 'justify-end' : 'justify-start')}>
              <div className={cn(
                'max-w-[85%] rounded-xl px-4 py-2.5 text-sm whitespace-pre-wrap',
                m.role === 'user' ? 'bg-primary text-primary-foreground' : 'bg-muted text-foreground',
              )}>
                {m.content}
                {m.role === 'assistant' && m.sources && m.sources.length > 0 && (
                  <div className="mt-2 pt-2 border-t border-border/50 flex flex-wrap gap-1.5">
                    {m.sources.map(s => (
                      <span key={s.chunkId} className="text-xs text-muted-foreground bg-surface rounded px-1.5 py-0.5">
                        {s.document}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ))
        )}
        {isSending && (
          <div className="flex justify-start">
            <div className="bg-muted rounded-xl px-4 py-2.5">
              <Spinner size="sm" />
            </div>
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      {error && (
        <Card className="mb-2 px-3 py-2 border-danger/30 bg-danger/5">
          <p className="text-xs text-danger">{error}</p>
        </Card>
      )}

      <div className="flex items-end gap-2">
        <textarea
          value={question}
          onChange={e => setQuestion(e.target.value)}
          onKeyDown={e => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              handleSend();
            }
          }}
          placeholder={hasCompletedDocuments ? 'Ask a question about this notebook…' : 'Waiting for a document to finish processing…'}
          disabled={!hasCompletedDocuments}
          rows={1}
          className="flex-1 resize-none rounded-lg border border-border bg-surface px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary disabled:opacity-50"
        />
        <Button
          size="md"
          onClick={handleSend}
          disabled={!question.trim() || isSending || !hasCompletedDocuments}
          leftIcon={<Send size={14} />}
        >
          Send
        </Button>
      </div>
    </div>
  );
}
