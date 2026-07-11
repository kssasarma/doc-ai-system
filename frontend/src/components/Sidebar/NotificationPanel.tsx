import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { X, Bell, Check, CheckCheck, HelpCircle, Share2, Folder, MessageSquare } from 'lucide-react';
import { AppNotification } from '../../types';
import {
  fetchNotifications,
  markNotificationRead,
  markAllNotificationsRead,
} from '../../services/notificationService';
import { useAuth } from '../../context/AuthContext';
import { formatTimestamp } from '../../utils/chatUtils';
import { cn } from '../../lib/cn';
import IconButton from '../ui/IconButton';
import Badge from '../ui/Badge';
import EmptyState from '../ui/EmptyState';
import { Skeleton } from '../ui/Skeleton';
import { useToast } from '../ui/Toast';

interface NotificationPanelProps {
  onClose: () => void;
  onCountChange?: (count: number) => void;
}

const TYPE_ICON: Record<string, React.ReactNode> = {
  ESCALATION_ANSWERED: <HelpCircle size={14} className="text-warning" />,
  SHARE_FORKED: <Share2 size={14} className="text-primary" />,
  COLLECTION_UPDATED: <Folder size={14} className="text-success" />,
  ANNOTATION_ADDED: <MessageSquare size={14} className="text-accent" />,
};

const NotificationPanel: React.FC<NotificationPanelProps> = ({ onClose, onCountChange }) => {
  const { token } = useAuth();
  const toast = useToast();
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
    toast.success('All notifications marked as read');
  };

  const unreadCount = notifications.filter(n => !n.read).length;

  return (
    <motion.div
      initial={{ opacity: 0, x: 8 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ duration: 0.16 }}
      className="absolute bottom-16 left-0 w-80 bg-surface rounded-2xl shadow-elevated dark:shadow-elevated-dark border border-border z-50 overflow-hidden"
    >
      <div className="flex items-center justify-between px-4 py-3 border-b border-border">
        <div className="flex items-center gap-2">
          <Bell size={15} className="text-muted-foreground" />
          <span className="text-sm font-semibold text-foreground">Notifications</span>
          {unreadCount > 0 && <Badge variant="solid">{unreadCount}</Badge>}
        </div>
        <div className="flex items-center gap-1">
          {unreadCount > 0 && (
            <IconButton label="Mark all read" variant="ghost" size="sm" onClick={handleMarkAllRead}>
              <CheckCheck size={14} />
            </IconButton>
          )}
          <IconButton label="Close" variant="ghost" size="sm" onClick={onClose}>
            <X size={14} />
          </IconButton>
        </div>
      </div>

      <div className="max-h-96 overflow-y-auto">
        {isLoading ? (
          <div className="p-4 space-y-3">
            {[0, 1, 2].map(i => <Skeleton key={i} className="h-12 w-full" />)}
          </div>
        ) : notifications.length === 0 ? (
          <EmptyState
            icon={Bell}
            title="No notifications yet"
            description="Escalation replies, shares, and collection updates will show up here."
          />
        ) : (
          notifications.map(n => (
            <div
              key={n.id}
              className={cn(
                'flex items-start gap-3 px-4 py-3 hover:bg-surface-hover transition-colors border-b border-border last:border-0',
                !n.read && 'bg-primary/5',
              )}
            >
              <div className="mt-0.5 flex-shrink-0">
                {TYPE_ICON[n.type] ?? <Bell size={14} className="text-muted-foreground" />}
              </div>
              <div className="flex-1 min-w-0">
                <p className={cn('text-sm leading-snug text-foreground', !n.read && 'font-medium')}>
                  {n.title}
                </p>
                {n.body && <p className="text-xs text-muted-foreground mt-0.5 line-clamp-2">{n.body}</p>}
                <p className="text-xs text-muted-foreground mt-1">
                  {formatTimestamp(new Date(n.createdAt).getTime())}
                </p>
              </div>
              {!n.read && (
                <IconButton
                  label="Mark as read"
                  variant="ghost"
                  size="sm"
                  className="flex-shrink-0"
                  onClick={() => handleMarkRead(n.id)}
                >
                  <Check size={13} />
                </IconButton>
              )}
            </div>
          ))
        )}
      </div>
    </motion.div>
  );
};

export default NotificationPanel;
