import React, { useState, useRef, useEffect } from 'react';
import { MessageCircle, Trash2, MoreVertical, Pin, PinOff, Pencil, Check, X } from 'lucide-react';
import { ChatSession } from '../../types';
import { formatTimestamp, truncateText } from '../../utils/chatUtils';

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
  const [showMenu, setShowMenu] = useState(false);
  const [isRenaming, setIsRenaming] = useState(false);
  const [renameValue, setRenameValue] = useState(session.title);
  const renameInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isRenaming) renameInputRef.current?.focus();
  }, [isRenaming]);

  const handleDelete = (e: React.MouseEvent) => {
    e.stopPropagation();
    onDelete();
    setShowMenu(false);
  };

  const handlePin = (e: React.MouseEvent) => {
    e.stopPropagation();
    onPin();
    setShowMenu(false);
  };

  const startRename = (e: React.MouseEvent) => {
    e.stopPropagation();
    setRenameValue(session.title);
    setIsRenaming(true);
    setShowMenu(false);
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
    <div
      className={`group relative p-3 rounded-lg cursor-pointer transition-all ${
        isActive ? 'bg-blue-600 text-white' : 'bg-gray-800 text-gray-300 hover:bg-gray-700'
      }`}
      onClick={!isRenaming ? onSelect : undefined}
    >
      <div className="flex items-start gap-2">
        <div className="mt-0.5 flex-shrink-0 flex items-center gap-1">
          {session.pinned && (
            <Pin size={11} className={isActive ? 'text-blue-200' : 'text-yellow-400'} />
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
                className="flex-1 text-xs bg-gray-700 text-white rounded px-1.5 py-0.5 border border-blue-400 focus:outline-none"
              />
              <button onClick={e => { e.stopPropagation(); commitRename(); }} className="text-green-400 hover:text-green-300">
                <Check size={12} />
              </button>
              <button onClick={e => { e.stopPropagation(); cancelRename(); }} className="text-gray-400 hover:text-gray-200">
                <X size={12} />
              </button>
            </div>
          ) : (
            <div className="font-medium text-sm mb-0.5 leading-snug">
              {truncateText(session.title, 32)}
            </div>
          )}

          {!isRenaming && (
            <div className="text-xs opacity-60">
              {session.messages.length} msgs • {formatTimestamp(session.updatedAt)}
            </div>
          )}

          {!isRenaming && session.tags && session.tags.length > 0 && (
            <div className="flex flex-wrap gap-1 mt-1">
              {session.tags.slice(0, 3).map(tag => (
                <span
                  key={tag}
                  className={`inline-block px-1.5 py-0 rounded text-xs ${
                    isActive ? 'bg-blue-500 text-blue-100' : 'bg-gray-700 text-gray-400'
                  }`}
                >
                  {tag}
                </span>
              ))}
            </div>
          )}
        </div>

        {!isRenaming && (
          <div className="relative flex-shrink-0">
            <button
              onClick={e => { e.stopPropagation(); setShowMenu(v => !v); }}
              className="opacity-0 group-hover:opacity-100 p-1 hover:bg-gray-600 rounded transition-all"
            >
              <MoreVertical size={13} />
            </button>

            {showMenu && (
              <div className="absolute right-0 top-6 bg-gray-700 rounded-lg shadow-lg py-1 z-20 w-36">
                <button
                  onClick={handlePin}
                  className="flex items-center gap-2 w-full px-3 py-1.5 text-xs text-gray-300 hover:bg-gray-600 transition-colors"
                >
                  {session.pinned ? <PinOff size={12} /> : <Pin size={12} />}
                  {session.pinned ? 'Unpin' : 'Pin'}
                </button>
                <button
                  onClick={startRename}
                  className="flex items-center gap-2 w-full px-3 py-1.5 text-xs text-gray-300 hover:bg-gray-600 transition-colors"
                >
                  <Pencil size={12} />
                  Rename
                </button>
                <button
                  onClick={handleDelete}
                  className="flex items-center gap-2 w-full px-3 py-1.5 text-xs text-red-300 hover:bg-gray-600 transition-colors"
                >
                  <Trash2 size={12} />
                  Delete
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default SessionItem;
