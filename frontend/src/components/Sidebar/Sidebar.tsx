import React, { Suspense, lazy, useState, useMemo, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Plus, Menu as MenuIcon, X, Bookmark, Bell, Folder, Key, BookOpen } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { ChatSession } from '../../types';
import { Search } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { fetchUnreadCount } from '../../services/notificationService';
import { cn } from '../../lib/cn';
import { EASE_OUT } from '../../lib/motion';
import Button from '../ui/Button';
import IconButton from '../ui/IconButton';
import { Skeleton } from '../ui/Skeleton';
import { useCommandPalette } from '../CommandPalette/CommandPaletteProvider';

const SessionItem = lazy(() => import('./SessionItem'));
const NotificationPanel = lazy(() => import('./NotificationPanel'));
const TenantSwitcher = lazy(() => import('./TenantSwitcher'));

interface SidebarProps {
  sessions: ChatSession[];
  activeSessionId: string;
  onCreateSession: () => void;
  onSelectSession: (sessionId: string) => void;
  onDeleteSession: (sessionId: string) => void;
  onPinSession: (sessionId: string) => void;
  onRenameSession: (sessionId: string, title: string) => void;
  isCollapsed: boolean;
  onToggleCollapse: () => void;
  /** Set when the sessions list failed to load — rendered as a retry banner instead of silently
   * showing an empty list indistinguishable from "you have no chats yet" (see Phase 6.1). */
  error?: string | null;
  onRetry?: () => void;
}

const NAV_ACTIONS = (navigate: ReturnType<typeof useNavigate>) => [
  { key: 'bookmarks', label: 'Bookmarks', icon: Bookmark, onClick: () => navigate('/bookmarks') },
  { key: 'collections', label: 'Collections', icon: Folder, onClick: () => navigate('/collections') },
  { key: 'faq', label: 'FAQ', icon: BookOpen, onClick: () => navigate('/faq') },
  { key: 'subscriptions', label: 'Subscriptions', icon: Bell, onClick: () => navigate('/subscriptions') },
  { key: 'api-keys', label: 'API Keys', icon: Key, onClick: () => navigate('/api-keys') },
];

const Sidebar: React.FC<SidebarProps> = ({
  sessions,
  activeSessionId,
  onCreateSession,
  onSelectSession,
  onDeleteSession,
  onPinSession,
  onRenameSession,
  isCollapsed,
  onToggleCollapse,
  error,
  onRetry,
}) => {
  const { token } = useAuth();
  const navigate = useNavigate();
  const { open: openPalette } = useCommandPalette();
  const [tagFilter, setTagFilter] = useState<string | null>(null);
  const [notifOpen, setNotifOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);

  useEffect(() => {
    if (!token) return;
    fetchUnreadCount(token).then(res => {
      if (res.success && res.data != null) setUnreadCount(res.data);
    });
    const interval = setInterval(() => {
      fetchUnreadCount(token).then(res => {
        if (res.success && res.data != null) setUnreadCount(res.data);
      });
    }, 60_000);
    return () => clearInterval(interval);
  }, [token]);

  const allTags = useMemo(() => {
    const tagSet = new Set<string>();
    sessions.forEach(s => s.tags?.forEach(t => tagSet.add(t)));
    return Array.from(tagSet).sort();
  }, [sessions]);

  const sortedSessions = useMemo(() => {
    const filtered = tagFilter
      ? sessions.filter(s => s.tags?.includes(tagFilter))
      : sessions;
    return [...filtered].sort((a, b) => {
      if ((b.pinned ? 1 : 0) !== (a.pinned ? 1 : 0)) return (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0);
      return b.updatedAt - a.updatedAt;
    });
  }, [sessions, tagFilter]);

  return (
    <motion.div
      animate={{ width: isCollapsed ? 64 : 320 }}
      transition={{ duration: 0.28, ease: EASE_OUT }}
      className="bg-surface text-foreground h-full flex flex-col relative border-r border-border flex-shrink-0 overflow-hidden"
    >
      {/* Header */}
      <div className="p-4 border-b border-border flex items-center justify-between gap-2">
        <IconButton label={isCollapsed ? 'Expand sidebar' : 'Collapse sidebar'} variant="ghost" onClick={onToggleCollapse}>
          {isCollapsed ? <MenuIcon size={18} /> : <X size={18} />}
        </IconButton>

        {!isCollapsed && (
          <Button onClick={onCreateSession} leftIcon={<Plus size={16} />} size="sm" className="flex-1">
            New Chat
          </Button>
        )}

        <IconButton label="Open command palette (Ctrl+K)" variant="ghost" onClick={openPalette}>
          <Search size={16} />
        </IconButton>
      </div>

      {/* Tag filter bar */}
      {!isCollapsed && allTags.length > 0 && (
        <div className="px-3 py-2 border-b border-border flex flex-wrap gap-1">
          <button
            onClick={() => setTagFilter(null)}
            className={cn(
              'px-2 py-0.5 rounded text-xs transition-colors',
              tagFilter === null ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground hover:bg-surface-hover',
            )}
          >
            All
          </button>
          {allTags.map(tag => (
            <button
              key={tag}
              onClick={() => setTagFilter(tag === tagFilter ? null : tag)}
              className={cn(
                'px-2 py-0.5 rounded text-xs transition-colors',
                tagFilter === tag ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground hover:bg-surface-hover',
              )}
            >
              {tag}
            </button>
          ))}
        </div>
      )}

      {/* Sessions List */}
      <div className="flex-1 overflow-y-auto p-2">
        {error && !isCollapsed && (
          <div className="mb-2 p-3 rounded-lg bg-danger/10 text-xs text-danger space-y-2">
            <p>Couldn't load your chats: {error}</p>
            {onRetry && (
              <button onClick={onRetry} className="font-medium underline hover:no-underline">
                Retry
              </button>
            )}
          </div>
        )}
        {isCollapsed ? (
          <div className="flex flex-col gap-2">
            {sortedSessions.slice(0, 5).map(session => (
              <button
                key={session.chatId}
                onClick={() => onSelectSession(session.chatId)}
                title={session.title}
                aria-label={session.title}
                className={cn(
                  'w-12 h-12 rounded-lg flex items-center justify-center text-xs font-medium transition-colors',
                  session.chatId === activeSessionId
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-muted text-muted-foreground hover:bg-surface-hover',
                )}
              >
                {session.title.charAt(0).toUpperCase()}
              </button>
            ))}
          </div>
        ) : (
          <div className="space-y-1">
            <Suspense fallback={<div className="space-y-2 p-1">{[0, 1, 2].map(i => <Skeleton key={i} className="h-14 rounded-lg" />)}</div>}>
              {sortedSessions.map(session => (
                <SessionItem
                  key={session.chatId}
                  session={session}
                  isActive={session.chatId === activeSessionId}
                  onSelect={() => onSelectSession(session.chatId)}
                  onDelete={() => onDeleteSession(session.chatId)}
                  onPin={() => onPinSession(session.chatId)}
                  onRename={(title) => onRenameSession(session.chatId, title)}
                />
              ))}
            </Suspense>
            {sortedSessions.length === 0 && (
              <div className="text-muted-foreground text-sm p-4 text-center">
                {tagFilter ? `No chats tagged "${tagFilter}"` : 'No chat sessions yet. Create your first one!'}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Footer — workspace switcher + destination shortcuts. Account-level actions (profile,
          Preferences, Admin Panel, sign out, theme) live in the persistent top-right AccountMenu
          (see AppShell), not here — one consistent place for those across the whole app. */}
      <div className={cn('border-t border-border p-3 flex flex-col gap-1.5', isCollapsed && 'items-center')}>
        <Suspense fallback={null}>
          <TenantSwitcher isCollapsed={isCollapsed} />
        </Suspense>

        <div className={cn('flex items-center gap-0.5', isCollapsed ? 'flex-col' : 'justify-between')}>
          <div className="relative">
            <IconButton
              label={unreadCount > 0 ? `Notifications (${unreadCount} unread)` : 'Notifications'}
              variant="ghost"
              size="sm"
              onClick={() => setNotifOpen(v => !v)}
            >
              <Bell size={15} />
            </IconButton>
            <AnimatePresence>
              {unreadCount > 0 && (
                <motion.span
                  initial={{ scale: 0 }}
                  animate={{ scale: 1 }}
                  exit={{ scale: 0 }}
                  className="pointer-events-none absolute -top-0.5 -right-0.5 w-4 h-4 bg-danger text-danger-foreground rounded-full text-[10px] flex items-center justify-center leading-none"
                >
                  {unreadCount > 9 ? '9+' : unreadCount}
                </motion.span>
              )}
            </AnimatePresence>
          </div>

          {NAV_ACTIONS(navigate).map(({ key, label, icon: Icon, onClick }) => (
            <IconButton key={key} label={label} variant="ghost" size="sm" onClick={onClick}>
              <Icon size={15} />
            </IconButton>
          ))}
        </div>
      </div>

      {/* Notification panel */}
      {notifOpen && (
        <Suspense fallback={null}>
          <NotificationPanel
            onClose={() => setNotifOpen(false)}
            onCountChange={setUnreadCount}
          />
        </Suspense>
      )}
    </motion.div>
  );
};

export default Sidebar;
