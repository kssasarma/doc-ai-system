import React, { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bookmark, Search, Trash2, ArrowLeft, Tag, MessageSquare } from 'lucide-react';
import { motion } from 'framer-motion';
import { Bookmark as BookmarkType } from '../../types';
import { fetchBookmarks, deleteBookmark } from '../../services/bookmarkService';
import { useAuth } from '../../context/AuthContext';
import { formatTimestamp } from '../../utils/chatUtils';
import { cn } from '../../lib/cn';
import { fadeInUp, staggerContainer } from '../../lib/motion';
import IconButton from '../ui/IconButton';
import { Card } from '../ui/Card';
import Badge from '../ui/Badge';
import EmptyState from '../ui/EmptyState';
import { SkeletonCard } from '../ui/Skeleton';
import PageHeader from '../ui/PageHeader';
import Input from '../ui/Input';
import { useToast } from '../ui/Toast';

const BookmarksPage: React.FC = () => {
  const { token } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
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
    if (res.success) {
      setBookmarks(prev => prev.filter(b => b.id !== id));
      toast.success('Bookmark deleted');
    } else {
      toast.error(res.error ?? 'Failed to delete bookmark');
    }
  };

  const handleReask = (b: BookmarkType) => {
    navigate(`/chat/${b.chatId}`);
  };

  return (
    <div className="min-h-full bg-background">
      <div className="max-w-3xl mx-auto px-6 py-6">
        {/* Header */}
        <div className="flex items-start gap-3">
          <IconButton label="Go back" variant="ghost" onClick={() => navigate(-1)}>
            <ArrowLeft size={18} />
          </IconButton>
          <div className="flex-1">
            <PageHeader
              title="Bookmarks"
              description="Assistant answers you've saved for later."
              actions={<span className="text-sm text-muted-foreground">{bookmarks.length} saved</span>}
            />
          </div>
        </div>

        <div className="space-y-4">
          {/* Search + tag filters */}
          <div className="flex flex-col gap-3">
            <div className="relative">
              <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none" />
              <Input
                type="text"
                aria-label="Search bookmarks"
                placeholder="Search bookmarks…"
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                className="pl-9"
              />
            </div>

            {allTags.length > 0 && (
              <div className="flex flex-wrap gap-2">
                <button
                  onClick={() => setTagFilter(null)}
                  className={cn(
                    'flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium transition-colors',
                    tagFilter === null ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground hover:bg-surface-hover',
                  )}
                >
                  All
                </button>
                {allTags.map(tag => (
                  <button
                    key={tag}
                    onClick={() => setTagFilter(tagFilter === tag ? null : tag)}
                    className={cn(
                      'flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium transition-colors',
                      tagFilter === tag ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground hover:bg-surface-hover',
                    )}
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
            <div className="space-y-3">
              {[0, 1, 2].map(i => <SkeletonCard key={i} />)}
            </div>
          ) : filtered.length === 0 ? (
            <EmptyState
              icon={Bookmark}
              title="No bookmarks yet"
              description="Bookmark any assistant answer to save it here."
            />
          ) : (
            <motion.div variants={staggerContainer} initial="hidden" animate="visible" className="space-y-3">
              {filtered.map(bookmark => (
                <motion.div key={bookmark.id} variants={fadeInUp}>
                  <Card className="p-4 hover:border-primary/30 transition-colors">
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex-1 min-w-0">
                        {bookmark.title && (
                          <h3 className="font-medium text-foreground mb-1 truncate">{bookmark.title}</h3>
                        )}
                        {bookmark.messageExcerpt && (
                          <p className="text-sm text-muted-foreground line-clamp-3 leading-relaxed">
                            {bookmark.messageExcerpt}
                          </p>
                        )}
                        {bookmark.note && (
                          <p className="text-xs text-primary bg-primary/10 rounded px-2 py-1 mt-2 italic">
                            {bookmark.note}
                          </p>
                        )}
                        <div className="flex items-center gap-3 mt-2 flex-wrap">
                          {bookmark.tags && bookmark.tags.length > 0 && (
                            <div className="flex gap-1 flex-wrap">
                              {bookmark.tags.map(tag => (
                                <Badge key={tag} variant="neutral">{tag}</Badge>
                              ))}
                            </div>
                          )}
                          {bookmark.createdAt && (
                            <span className="text-xs text-muted-foreground">
                              {formatTimestamp(new Date(bookmark.createdAt).getTime())}
                            </span>
                          )}
                        </div>
                      </div>

                      <div className="flex items-center gap-1 flex-shrink-0">
                        <IconButton
                          label="Open original chat"
                          variant="ghost"
                          size="sm"
                          onClick={() => handleReask(bookmark)}
                        >
                          <MessageSquare size={14} />
                        </IconButton>
                        <IconButton
                          label="Delete bookmark"
                          variant="danger"
                          size="sm"
                          onClick={() => handleDelete(bookmark.id)}
                        >
                          <Trash2 size={14} />
                        </IconButton>
                      </div>
                    </div>
                  </Card>
                </motion.div>
              ))}
            </motion.div>
          )}
        </div>
      </div>
    </div>
  );
};

export default BookmarksPage;
