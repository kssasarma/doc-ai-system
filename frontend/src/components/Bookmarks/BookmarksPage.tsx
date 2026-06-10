import React, { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bookmark, Search, Trash2, ArrowLeft, Tag, MessageSquare } from 'lucide-react';
import { Bookmark as BookmarkType } from '../../types';
import { fetchBookmarks, deleteBookmark } from '../../services/bookmarkService';
import { useAuth } from '../../context/AuthContext';
import { formatTimestamp } from '../../utils/chatUtils';

const BookmarksPage: React.FC = () => {
  const { token } = useAuth();
  const navigate = useNavigate();
  const [bookmarks, setBookmarks] = useState<BookmarkType[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [tagFilter, setTagFilter] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    setIsLoading(true);
    fetchBookmarks(token).then(res => {
      if (res.success && res.data) setBookmarks(res.data);
    }).finally(() => setIsLoading(false));
  }, [token]);

  const allTags = useMemo(() => {
    const s = new Set<string>();
    bookmarks.forEach(b => b.tags?.forEach(t => s.add(t)));
    return Array.from(s).sort();
  }, [bookmarks]);

  const filtered = useMemo(() => {
    let list = bookmarks;
    if (tagFilter) list = list.filter(b => b.tags?.includes(tagFilter));
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      list = list.filter(b =>
        b.title?.toLowerCase().includes(q) ||
        b.messageExcerpt?.toLowerCase().includes(q) ||
        b.note?.toLowerCase().includes(q)
      );
    }
    return list;
  }, [bookmarks, tagFilter, searchQuery]);

  const handleDelete = async (id: string) => {
    if (!token) return;
    const res = await deleteBookmark(id, token);
    if (res.success) setBookmarks(prev => prev.filter(b => b.id !== id));
  };

  const handleReask = (b: BookmarkType) => {
    navigate(`/chat/${b.chatId}`);
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b border-gray-200 px-6 py-4">
        <div className="max-w-3xl mx-auto flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <button
              onClick={() => navigate(-1)}
              className="p-2 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
            >
              <ArrowLeft size={18} />
            </button>
            <div className="flex items-center gap-2">
              <Bookmark size={20} className="text-yellow-500" />
              <h1 className="text-xl font-semibold text-gray-900">Bookmarks</h1>
            </div>
          </div>
          <span className="text-sm text-gray-500">{bookmarks.length} saved</span>
        </div>
      </div>

      <div className="max-w-3xl mx-auto px-6 py-6 space-y-4">
        {/* Search + tag filters */}
        <div className="flex flex-col gap-3">
          <div className="relative">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              type="text"
              placeholder="Search bookmarks…"
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
            />
          </div>

          {allTags.length > 0 && (
            <div className="flex flex-wrap gap-2">
              <button
                onClick={() => setTagFilter(null)}
                className={`flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium transition-colors ${
                  tagFilter === null ? 'bg-blue-600 text-white' : 'bg-gray-200 text-gray-600 hover:bg-gray-300'
                }`}
              >
                All
              </button>
              {allTags.map(tag => (
                <button
                  key={tag}
                  onClick={() => setTagFilter(tagFilter === tag ? null : tag)}
                  className={`flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium transition-colors ${
                    tagFilter === tag ? 'bg-blue-600 text-white' : 'bg-gray-200 text-gray-600 hover:bg-gray-300'
                  }`}
                >
                  <Tag size={10} />
                  {tag}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Bookmark list */}
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600" />
          </div>
        ) : filtered.length === 0 ? (
          <div className="text-center py-16 text-gray-500">
            <Bookmark size={40} className="mx-auto mb-3 text-gray-300" />
            <p className="font-medium">No bookmarks yet</p>
            <p className="text-sm mt-1">Bookmark any assistant answer to save it here</p>
          </div>
        ) : (
          <div className="space-y-3">
            {filtered.map(bookmark => (
              <div
                key={bookmark.id}
                className="bg-white border border-gray-200 rounded-xl p-4 hover:border-blue-200 transition-colors"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="flex-1 min-w-0">
                    {bookmark.title && (
                      <h3 className="font-medium text-gray-900 mb-1 truncate">{bookmark.title}</h3>
                    )}
                    {bookmark.messageExcerpt && (
                      <p className="text-sm text-gray-600 line-clamp-3 leading-relaxed">
                        {bookmark.messageExcerpt}
                      </p>
                    )}
                    {bookmark.note && (
                      <p className="text-xs text-blue-700 bg-blue-50 rounded px-2 py-1 mt-2 italic">
                        {bookmark.note}
                      </p>
                    )}
                    <div className="flex items-center gap-3 mt-2 flex-wrap">
                      {bookmark.tags && bookmark.tags.length > 0 && (
                        <div className="flex gap-1 flex-wrap">
                          {bookmark.tags.map(tag => (
                            <span key={tag} className="px-1.5 py-0.5 bg-gray-100 text-gray-500 rounded text-xs">
                              {tag}
                            </span>
                          ))}
                        </div>
                      )}
                      {bookmark.createdAt && (
                        <span className="text-xs text-gray-400">
                          {formatTimestamp(new Date(bookmark.createdAt).getTime())}
                        </span>
                      )}
                    </div>
                  </div>

                  <div className="flex items-center gap-1 flex-shrink-0">
                    <button
                      onClick={() => handleReask(bookmark)}
                      title="Open original chat"
                      className="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                    >
                      <MessageSquare size={14} />
                    </button>
                    <button
                      onClick={() => handleDelete(bookmark.id)}
                      title="Delete bookmark"
                      className="p-1.5 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default BookmarksPage;
