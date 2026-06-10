import React, { useState } from 'react';
import { MessageCircle, Trash2, MoreVertical } from 'lucide-react';
import { ChatSession } from '../../types';
import { formatTimestamp, truncateText } from '../../utils/chatUtils';

interface SessionItemProps {
  session: ChatSession;
  isActive: boolean;
  onSelect: () => void;
  onDelete: () => void;
}

const SessionItem: React.FC<SessionItemProps> = ({
  session,
  isActive,
  onSelect,
  onDelete
}) => {
  const [showMenu, setShowMenu] = useState(false);

  const handleDelete = (e: React.MouseEvent) => {
    e.stopPropagation();
    onDelete();
    setShowMenu(false);
  };

  return (
    <div
      className={`group relative p-3 rounded-lg cursor-pointer transition-all ${
        isActive
          ? 'bg-blue-600 text-white'
          : 'bg-gray-800 text-gray-300 hover:bg-gray-700'
      }`}
      onClick={onSelect}
    >
      <div className="flex items-start gap-3">
        <MessageCircle size={16} className="mt-1 flex-shrink-0" />
        <div className="flex-1 min-w-0">
          <div className="font-medium text-sm mb-1">
            {truncateText(session.title, 30)}
          </div>
          <div className="text-xs opacity-70">
            {session.messages.length} messages • {formatTimestamp(session.updatedAt)}
          </div>
        </div>
        
        <div className="relative">
          <button
            onClick={(e) => {
              e.stopPropagation();
              setShowMenu(!showMenu);
            }}
            className="opacity-0 group-hover:opacity-100 p-1 hover:bg-gray-600 rounded transition-all"
          >
            <MoreVertical size={14} />
          </button>
          
          {showMenu && (
            <div className="absolute right-0 top-6 bg-gray-700 rounded-lg shadow-lg py-1 z-10">
              <button
                onClick={handleDelete}
                className="flex items-center gap-2 w-full px-3 py-2 text-sm text-red-300 hover:bg-gray-600 transition-colors"
              >
                <Trash2 size={14} />
                Delete
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default SessionItem;