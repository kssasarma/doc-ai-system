import React, { useEffect, useState } from 'react';
import { X, Bell, Check, CheckCheck, HelpCircle, Share2, Folder, MessageSquare } from 'lucide-react';
import { AppNotification } from '../../types';
import {
  fetchNotifications,
  markNotificationRead,
  markAllNotificationsRead,
} from '../../services/notificationService';
import { useAuth } from '../../context/AuthContext';
import { formatTimestamp } from '../../utils/chatUtils';

interface NotificationPanelProps {
  onClose: () => void;
  onCountChange?: (count: number) => void;
}

const TYPE_ICON: Record<string, React.ReactNode> = {
  ESCALATION_ANSWERED: <HelpCircle size={14} className="text-orange-500" />,
  SHARE_FORKED: <Share2 size={14} className="text-blue-500" />,
  COLLECTION_UPDATED: <Folder size={14} className="text-green-500" />,
  ANNOTATION_ADDED: <MessageSquare size={14} className="text-purple-500" />,
};

const NotificationPanel: React.FC<NotificationPanelProps> = ({ onClose, onCountChange }) => {
  const { token } = useAuth();
  const [notifications, setNotifications] = useState<AppNotification[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!token) return;
    fetchNotifications(token).then(res => {
      if (res.success && res.data) {
        setNotifications(res.data);
        const unread = res.data.filter(n => !n.read).length;
        onCountChange?.(unread);
      }
    }).finally(() => setIsLoading(false));
  }, [token]);

  const handleMarkRead = async (id: string) => {
    if (!token) return;
    await markNotificationRead(id, token);
    setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: true } : n));
    onCountChange?.(notifications.filter(n => !n.read && n.id !== id).length);
  };

  const handleMarkAllRead = async () => {
    if (!token) return;
    await markAllNotificationsRead(token);
    setNotifications(prev => prev.map(n => ({ ...n, read: true })));
    onCountChange?.(0);
  };

  const unreadCount = notifications.filter(n => !n.read).length;

  return (
    <div className="absolute bottom-16 left-0 w-80 bg-white rounded-2xl shadow-2xl border border-gray-100 z-50 overflow-hidden">
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100">
        <div className="flex items-center gap-2">
          <Bell size={15} className="text-gray-600" />
          <span className="text-sm font-semibold text-gray-900">Notifications</span>
          {unreadCount > 0 && (
            <span className="px-1.5 py-0.5 bg-blue-600 text-white text-xs rounded-full leading-none">
              {unreadCount}
            </span>
          )}
        </div>
        <div className="flex items-center gap-1">
          {unreadCount > 0 && (
            <button
              onClick={handleMarkAllRead}
              title="Mark all read"
              className="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
            >
              <CheckCheck size={14} />
            </button>
          )}
          <button
            onClick={onClose}
            className="p-1.5 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
          >
            <X size={14} />
          </button>
        </div>
      </div>

      <div className="max-h-96 overflow-y-auto">
        {isLoading ? (
          <div className="flex items-center justify-center py-8">
            <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-600" />
          </div>
        ) : notifications.length === 0 ? (
          <div className="text-center py-10 text-gray-400 text-sm">
            <Bell size={28} className="mx-auto mb-2 text-gray-200" />
            No notifications yet
          </div>
        ) : (
          notifications.map(n => (
            <div
              key={n.id}
              className={`flex items-start gap-3 px-4 py-3 hover:bg-gray-50 transition-colors border-b border-gray-50 last:border-0 ${
                !n.read ? 'bg-blue-50/40' : ''
              }`}
            >
              <div className="mt-0.5 flex-shrink-0">
                {TYPE_ICON[n.type] ?? <Bell size={14} className="text-gray-400" />}
              </div>
              <div className="flex-1 min-w-0">
                <p className={`text-sm leading-snug ${!n.read ? 'font-medium text-gray-900' : 'text-gray-700'}`}>
                  {n.title}
                </p>
                {n.body && <p className="text-xs text-gray-500 mt-0.5 line-clamp-2">{n.body}</p>}
                <p className="text-xs text-gray-400 mt-1">
                  {formatTimestamp(new Date(n.createdAt).getTime())}
                </p>
              </div>
              {!n.read && (
                <button
                  onClick={() => handleMarkRead(n.id)}
                  title="Mark as read"
                  className="p-1.5 text-gray-300 hover:text-blue-500 rounded-lg transition-colors flex-shrink-0"
                >
                  <Check size={13} />
                </button>
              )}
            </div>
          ))
        )}
      </div>
    </div>
  );
};

export default NotificationPanel;
