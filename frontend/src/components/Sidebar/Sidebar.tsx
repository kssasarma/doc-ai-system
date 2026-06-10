import React, { Suspense, lazy } from 'react';
import { Plus, Menu, X, Settings, LogOut } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { ChatSession } from '../../types';
import { useAuth } from '../../context/AuthContext';

const SessionItem = lazy(() => import('./SessionItem'));

interface SidebarProps {
  sessions: ChatSession[];
  activeSessionId: string;
  onCreateSession: () => void;
  onSelectSession: (sessionId: string) => void;
  onDeleteSession: (sessionId: string) => void;
  isCollapsed: boolean;
  onToggleCollapse: () => void;
}

const Sidebar: React.FC<SidebarProps> = ({
  sessions,
  activeSessionId,
  onCreateSession,
  onSelectSession,
  onDeleteSession,
  isCollapsed,
  onToggleCollapse,
}) => {
  const { user, logout, isAdmin } = useAuth();
  const navigate = useNavigate();

  return (
    <div className={`bg-gray-900 text-white h-screen transition-all duration-300 ${
      isCollapsed ? 'w-16' : 'w-80'
    } flex flex-col`}>
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
          <div className="space-y-2">
            <Suspense fallback={<div className="p-2 text-gray-400 text-sm">Loading...</div>}>
              {sessions.map(session => (
                <SessionItem
                  key={session.chatId}
                  session={session}
                  isActive={session.chatId === activeSessionId}
                  onSelect={() => onSelectSession(session.chatId)}
                  onDelete={() => onDeleteSession(session.chatId)}
                />
              ))}
            </Suspense>
            {sessions.length === 0 && (
              <div className="text-gray-400 text-sm p-4 text-center">
                No chat sessions yet. Create your first one!
              </div>
            )}
          </div>
        )}
      </div>

      {/* Footer: user info + actions */}
      <div className={`border-t border-gray-700 p-3 ${isCollapsed ? 'flex flex-col items-center gap-2' : ''}`}>
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
        <div className={`flex ${isCollapsed ? 'flex-col' : 'flex-row'} gap-1`}>
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
    </div>
  );
};

export default Sidebar;
