import React, { Suspense, lazy, useState, useMemo, useEffect } from 'react';
import { Plus, Menu, X, Settings, LogOut, Bookmark, SlidersHorizontal, Bell, Folder, Key, BookOpen } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { ChatSession } from '../../types';
import { useAuth } from '../../context/AuthContext';
import { fetchUnreadCount } from '../../services/notificationService';

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
  onOpenPreferences: () => void;
  isCollapsed: boolean;
  onToggleCollapse: () => void;
}

const Sidebar: React.FC<SidebarProps> = ({
  sessions,
  activeSessionId,
  onCreateSession,
  onSelectSession,
  onDeleteSession,
  onPinSession,
  onRenameSession,
  onOpenPreferences,
  isCollapsed,
  onToggleCollapse,
}) => {
  const { user, logout, isAdmin, token } = useAuth();
  const navigate = useNavigate();
  const [tagFilter, setTagFilter] = useState<string | null>(null);
  const [notifOpen, setNotifOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);

  useEffect(() => {
    if (!token) return;
    fetchUnreadCount(token).then(res => {
      if (res.success && res.data != null) setUnreadCount(res.data);
    });
    // Poll every 60s for new notifications
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
    <div className={`bg-gray-900 text-white h-screen transition-all duration-300 ${
      isCollapsed ? 'w-16' : 'w-80'
    } flex flex-col relative`}>
      {/* Header */}
      <div className="p-4 border-b border-gray-700 flex items-center justify-between">
        <button
          onClick={onToggleCollapse}
          className="p-2 hover:bg-gray-800 rounded-lg transition-colors"
        >
          {isCollapsed ? <Menu size={20} /> : <X size={20} />}
        </button>

        {!isCollapsed && (
          <button
            onClick={onCreateSession}
            className="flex items-center gap-2 px-3 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors text-sm font-medium"
          >
            <Plus size={16} />
            New Chat
          </button>
        )}
      </div>

      {/* Tag filter bar */}
      {!isCollapsed && allTags.length > 0 && (
        <div className="px-3 py-2 border-b border-gray-800 flex flex-wrap gap-1">
          <button
            onClick={() => setTagFilter(null)}
            className={`px-2 py-0.5 rounded text-xs transition-colors ${
              tagFilter === null ? 'bg-blue-600 text-white' : 'bg-gray-800 text-gray-400 hover:bg-gray-700'
            }`}
          >
            All
          </button>
          {allTags.map(tag => (
            <button
              key={tag}
              onClick={() => setTagFilter(tag === tagFilter ? null : tag)}
              className={`px-2 py-0.5 rounded text-xs transition-colors ${
                tagFilter === tag ? 'bg-blue-600 text-white' : 'bg-gray-800 text-gray-400 hover:bg-gray-700'
              }`}
            >
              {tag}
            </button>
          ))}
        </div>
      )}

      {/* Sessions List */}
      <div className="flex-1 overflow-y-auto p-2">
        {isCollapsed ? (
          <div className="flex flex-col gap-2">
            {sessions.slice(0, 5).map(session => (
              <button
                key={session.chatId}
                onClick={() => onSelectSession(session.chatId)}
                className={`w-12 h-12 rounded-lg flex items-center justify-center text-xs font-medium transition-colors ${
                  session.chatId === activeSessionId
                    ? 'bg-blue-600 text-white'
                    : 'bg-gray-800 text-gray-300 hover:bg-gray-700'
                }`}
                title={session.title}
              >
                {session.title.charAt(0).toUpperCase()}
              </button>
            ))}
          </div>
        ) : (
          <div className="space-y-1">
            <Suspense fallback={<div className="p-2 text-gray-400 text-sm">Loading...</div>}>
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
              <div className="text-gray-400 text-sm p-4 text-center">
                {tagFilter ? `No chats tagged "${tagFilter}"` : 'No chat sessions yet. Create your first one!'}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Footer */}
      <div className={`border-t border-gray-700 p-3 ${isCollapsed ? 'flex flex-col items-center gap-2' : ''}`}>
        <Suspense fallback={null}>
          <TenantSwitcher isCollapsed={isCollapsed} />
        </Suspense>

        {!isCollapsed && user && (
          <div className="flex items-center gap-2 mb-2 px-1">
            <div className="w-7 h-7 rounded-full bg-blue-600 flex items-center justify-center text-xs font-bold flex-shrink-0">
              {user.username.charAt(0).toUpperCase()}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-sm font-medium text-gray-200 truncate">{user.username}</div>
              <div className="text-xs text-gray-500">{user.role}</div>
            </div>
          </div>
        )}

        <div className={`flex ${isCollapsed ? 'flex-col' : 'flex-row flex-wrap'} gap-1`}>
          {/* Notification bell */}
          <button
            onClick={() => setNotifOpen(v => !v)}
            title="Notifications"
            className="relative flex items-center gap-1.5 px-2 py-1.5 text-xs text-gray-400 hover:text-white hover:bg-gray-800 rounded-lg transition-colors"
          >
            <Bell size={14} />
            {!isCollapsed && 'Notifications'}
            {unreadCount > 0 && (
              <span className="absolute -top-0.5 -right-0.5 w-4 h-4 bg-red-500 text-white rounded-full text-[10px] flex items-center justify-center leading-none">
                {unreadCount > 9 ? '9+' : unreadCount}
              </span>
            )}
          </button>

          <button
            onClick={() => navigate('/bookmarks')}
            title="Bookmarks"
            className="flex items-center gap-1.5 px-2 py-1.5 text-xs text-gray-400 hover:text-white hover:bg-gray-800 rounded-lg transition-colors"
          >
            <Bookmark size={14} />
            {!isCollapsed && 'Bookmarks'}
          </button>

          <button
            onClick={() => navigate('/collections')}
            title="Collections"
            className="flex items-center gap-1.5 px-2 py-1.5 text-xs text-gray-400 hover:text-white hover:bg-gray-800 rounded-lg transition-colors"
          >
            <Folder size={14} />
            {!isCollapsed && 'Collections'}
          </button>

          <button
            onClick={() => navigate('/faq')}
            title="FAQ"
            className="flex items-center gap-1.5 px-2 py-1.5 text-xs text-gray-400 hover:text-white hover:bg-gray-800 rounded-lg transition-colors"
          >
            <BookOpen size={14} />
            {!isCollapsed && 'FAQ'}
          </button>

          <button
            onClick={() => navigate('/subscriptions')}
            title="Subscriptions"
            className="flex items-center gap-1.5 px-2 py-1.5 text-xs text-gray-400 hover:text-white hover:bg-gray-800 rounded-lg transition-colors"
          >
            <Bell size={14} />
            {!isCollapsed && 'Subscriptions'}
          </button>

          <button
            onClick={onOpenPreferences}
            title="Preferences"
            className="flex items-center gap-1.5 px-2 py-1.5 text-xs text-gray-400 hover:text-white hover:bg-gray-800 rounded-lg transition-colors"
          >
            <SlidersHorizontal size={14} />
            {!isCollapsed && 'Preferences'}
          </button>

          <button
            onClick={() => navigate('/api-keys')}
            title="API Keys"
            className="flex items-center gap-1.5 px-2 py-1.5 text-xs text-gray-400 hover:text-white hover:bg-gray-800 rounded-lg transition-colors"
          >
            <Key size={14} />
            {!isCollapsed && 'API Keys'}
          </button>

          {isAdmin && (
            <button
              onClick={() => navigate('/admin')}
              title="Admin Panel"
              className="flex items-center gap-1.5 px-2 py-1.5 text-xs text-gray-400 hover:text-white hover:bg-gray-800 rounded-lg transition-colors"
            >
              <Settings size={14} />
              {!isCollapsed && 'Admin'}
            </button>
          )}

          <button
            onClick={logout}
            title="Sign out"
            className="flex items-center gap-1.5 px-2 py-1.5 text-xs text-gray-400 hover:text-white hover:bg-gray-800 rounded-lg transition-colors"
          >
            <LogOut size={14} />
            {!isCollapsed && 'Sign out'}
          </button>
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
    </div>
  );
};

export default Sidebar;
