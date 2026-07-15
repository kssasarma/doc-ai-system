import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { Send, Sparkles, Clock, BookOpen, MessageSquarePlus } from 'lucide-react';
import { Link } from 'react-router-dom';
import { ChatSession } from '../../types';
import { useLibrary } from '../../hooks/useLibrary';
import { fadeIn } from '../../lib/motion';
import { cn } from '../../lib/cn';
import Badge from '../ui/Badge';
import { Skeleton } from '../ui/Skeleton';
import appConfig from '../../config/app.json';

const STARTER_PROMPTS = [
  'How do I get started?',
  'What changed in the latest release?',
  'Summarize the deployment process',
  'What are the known limitations?',
];

interface HomeScreenProps {
  onAsk: (content: string) => void;
  sessions: ChatSession[];
  onSelectSession: (chatId: string) => void;
}

const HomeScreen: React.FC<HomeScreenProps> = ({ onAsk, sessions, onSelectSession }) => {
  const [draft, setDraft] = useState('');
  const { data: documents, isLoading: isLoadingLibrary } = useLibrary();

  const recentDocuments = (documents ?? []).slice(0, 6);
  const recentSessions = sessions
    .slice()
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .slice(0, 5);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = draft.trim();
    if (!trimmed) return;
    onAsk(trimmed);
    setDraft('');
  };

  return (
    <motion.div
      variants={fadeIn}
      initial="hidden"
      animate="visible"
      className="flex-1 overflow-y-auto bg-background"
    >
      <div className="max-w-3xl mx-auto px-6 py-16">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-semibold text-foreground">
            What do you want to know about {appConfig.app.title}?
          </h1>
          <p className="text-sm text-muted-foreground mt-2">
            Ask a question and get an answer grounded in your team's documentation.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="relative mb-4">
          <input
            autoFocus
            value={draft}
            onChange={e => setDraft(e.target.value)}
            placeholder="Ask anything about your docs…"
            aria-label="Ask a question"
            className="w-full rounded-2xl border border-border bg-surface shadow-soft px-5 py-4 pr-14 text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary"
          />
          <button
            type="submit"
            disabled={!draft.trim()}
            aria-label="Ask"
            className={cn(
              'absolute right-2.5 top-1/2 -translate-y-1/2 flex items-center justify-center h-9 w-9 rounded-full transition-colors',
              draft.trim() ? 'bg-primary text-primary-foreground hover:bg-primary/90' : 'bg-muted text-muted-foreground cursor-not-allowed',
            )}
          >
            <Send size={16} />
          </button>
        </form>

        <div className="flex flex-wrap gap-2 justify-center mb-14">
          {STARTER_PROMPTS.map(prompt => (
            <button
              key={prompt}
              type="button"
              onClick={() => onAsk(prompt)}
              className="inline-flex items-center gap-1.5 text-xs font-medium text-muted-foreground bg-muted hover:bg-muted/70 rounded-full px-3 py-1.5 transition-colors"
            >
              <Sparkles size={12} />
              {prompt}
            </button>
          ))}
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-8">
          <section>
            <div className="flex items-center justify-between mb-3">
              <h2 className="flex items-center gap-1.5 text-sm font-semibold text-foreground">
                <BookOpen size={14} />
                Recently updated
              </h2>
              <Link to="/library" className="text-xs font-medium text-primary hover:underline">
                Browse library
              </Link>
            </div>
            {isLoadingLibrary ? (
              <div className="space-y-2">
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
              </div>
            ) : recentDocuments.length === 0 ? (
              <p className="text-xs text-muted-foreground">No documents available yet.</p>
            ) : (
              <ul className="space-y-1">
                {recentDocuments.map(doc => (
                  <li key={doc.id}>
                    <div className="flex items-center gap-2 rounded-lg px-3 py-2 hover:bg-muted transition-colors">
                      <span className="text-sm text-foreground truncate flex-1" title={doc.documentName}>
                        {doc.documentName}
                      </span>
                      <Badge variant="neutral">{doc.product}</Badge>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section>
            <h2 className="flex items-center gap-1.5 text-sm font-semibold text-foreground mb-3">
              <Clock size={14} />
              Recent chats
            </h2>
            {recentSessions.length === 0 ? (
              <p className="text-xs text-muted-foreground flex items-center gap-1.5">
                <MessageSquarePlus size={13} />
                Your conversations will show up here.
              </p>
            ) : (
              <ul className="space-y-1">
                {recentSessions.map(session => (
                  <li key={session.chatId}>
                    <button
                      type="button"
                      onClick={() => onSelectSession(session.chatId)}
                      className="w-full text-left rounded-lg px-3 py-2 hover:bg-muted transition-colors text-sm text-foreground truncate"
                    >
                      {session.title}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </div>
    </motion.div>
  );
};

export default HomeScreen;
