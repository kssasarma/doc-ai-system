import React, { useState, useRef, useEffect } from 'react';
import { motion } from 'framer-motion';
import { MessageCircle, Trash2, MoreVertical, Pin, PinOff, Pencil, Check, X } from 'lucide-react';
import { ChatSession } from '../../types';
import { formatTimestamp, truncateText } from '../../utils/chatUtils';
import { cn } from '../../lib/cn';
import Menu from '../ui/Menu';
import IconButton from '../ui/IconButton';

interface SessionItemProps {
  session: ChatSession;
  isActive: boolean;
  onSelect: () => void;
  onDelete: () => void;
  onPin: () => void;
  onRename: (title: string) => void;
}

const SessionItem: React.FC<SessionItemProps> = ({
  session,
  isActive,
  onSelect,
  onDelete,
  onPin,
  onRename,
}) => {
  const [isRenaming, setIsRenaming] = useState(false);
  const [renameValue, setRenameValue] = useState(session.title);
  const renameInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isRenaming) renameInputRef.current?.focus();
  }, [isRenaming]);

  const startRename = () => {
    setRenameValue(session.title);
    setIsRenaming(true);
  };

  const commitRename = () => {
    const trimmed = renameValue.trim();
    if (trimmed && trimmed !== session.title) onRename(trimmed);
    setIsRenaming(false);
  };

  const cancelRename = () => {
    setRenameValue(session.title);
    setIsRenaming(false);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') commitRename();
    else if (e.key === 'Escape') cancelRename();
  };

  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 4 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.18 }}
      className={cn(
        'group relative p-3 rounded-lg cursor-pointer transition-colors border-l-2',
        isActive
          ? 'bg-primary text-primary-foreground border-l-primary-foreground/60'
          : 'text-muted-foreground border-l-transparent hover:bg-surface-hover hover:text-foreground',
      )}
      onClick={!isRenaming ? onSelect : undefined}
      aria-current={isActive ? 'true' : undefined}
    >
      <div className="flex items-start gap-2">
        <div className="mt-0.5 flex-shrink-0 flex items-center gap-1">
          {session.pinned && (
            <Pin size={11} className={isActive ? 'text-primary-foreground/80' : 'text-warning'} />
          )}
          <MessageCircle size={14} />
        </div>

        <div className="flex-1 min-w-0">
          {isRenaming ? (
            <div className="flex items-center gap-1" onClick={e => e.stopPropagation()}>
              <input
                ref={renameInputRef}
                value={renameValue}
                onChange={e => setRenameValue(e.target.value)}
                onKeyDown={handleKeyDown}
                aria-label="Rename conversation"
                className="flex-1 text-xs bg-surface text-foreground rounded px-1.5 py-0.5 border border-primary focus:outline-none"
              />
              <button onClick={e => { e.stopPropagation(); commitRename(); }} aria-label="Save name" className="text-success hover:opacity-80">
                <Check size={12} />
              </button>
              <button onClick={e => { e.stopPropagation(); cancelRename(); }} aria-label="Cancel rename" className="opacity-70 hover:opacity-100">
                <X size={12} />
              </button>
            </div>
          ) : (
            <div className="font-medium text-sm mb-0.5 leading-snug">
              {truncateText(session.title, 32)}
            </div>
          )}

          {!isRenaming && (
            <div className={cn('text-xs', isActive ? 'text-primary-foreground/70' : 'text-muted-foreground')}>
              {session.messages.length} msgs • {formatTimestamp(session.updatedAt)}
            </div>
          )}

          {!isRenaming && session.tags && session.tags.length > 0 && (
            <div className="flex flex-wrap gap-1 mt-1">
              {session.tags.slice(0, 3).map(tag => (
                <span
                  key={tag}
                  className={cn(
                    'inline-block px-1.5 py-0 rounded text-xs',
                    isActive ? 'bg-white/20 text-primary-foreground' : 'bg-muted text-muted-foreground',
                  )}
                >
                  {tag}
                </span>
              ))}
            </div>
          )}
        </div>

        {!isRenaming && (
          <div className="flex-shrink-0 opacity-0 group-hover:opacity-100 transition-opacity" onClick={e => e.stopPropagation()}>
            <Menu
              trigger={
                <IconButton
                  label="Session actions"
                  variant="ghost"
                  size="sm"
                  className={isActive ? 'text-primary-foreground/80 hover:bg-white/15 hover:text-primary-foreground' : ''}
                >
                  <MoreVertical size={13} />
                </IconButton>
              }
              options={[
                {
                  key: 'pin', label: session.pinned ? 'Unpin' : 'Pin',
                  icon: session.pinned ? <PinOff size={12} /> : <Pin size={12} />,
                  onSelect: onPin,
                },
                { key: 'rename', label: 'Rename', icon: <Pencil size={12} />, onSelect: startRename },
                { key: 'delete', label: 'Delete', icon: <Trash2 size={12} />, onSelect: onDelete, danger: true },
              ]}
            />
          </div>
        )}
      </div>
    </motion.div>
  );
};

export default SessionItem;
