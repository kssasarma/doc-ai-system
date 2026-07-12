import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { MessageCircle, ThumbsUp, ChevronDown, ChevronUp, Search } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import { listApprovedFaq, markHelpful, type FaqEntry, type PagedFaqEntries } from '../../services/faqService';
import { useAuth } from '../../context/AuthContext';
import { fadeInUp, staggerContainer } from '../../lib/motion';
import PageHeader from '../ui/PageHeader';
import { Card } from '../ui/Card';
import Button from '../ui/Button';
import Badge from '../ui/Badge';
import Input from '../ui/Input';
import EmptyState from '../ui/EmptyState';
import { SkeletonCard } from '../ui/Skeleton';
import { useToast } from '../ui/Toast';

export default function FaqPage() {
  const { token } = useAuth();
  const toast = useToast();
  const [data, setData] = useState<PagedFaqEntries | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [product, setProduct] = useState('');
  const [version, setVersion] = useState('');
  const [search, setSearch] = useState('');
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  useEffect(() => {
    load();
  }, [product, version, page]);

  const load = async () => {
    setLoading(true);
    const result = await listApprovedFaq(product || undefined, version || undefined, page, 20);
    if (result.success && result.data) {
      setData(result.data);
      setError(null);
    } else {
      setError(result.error ?? 'Failed to load FAQ');
    }
    setLoading(false);
  };

  const handleHelpful = async (id: string) => {
    if (!token) return;
    await markHelpful(id, token);
    setData(prev => prev ? {
      ...prev,
      content: prev.content.map(e => e.id === id ? { ...e, helpfulCount: e.helpfulCount + 1 } : e)
    } : prev);
    toast.success('Thanks for the feedback!');
  };

  const filtered = (data?.content ?? []).filter(e =>
    !search || e.question.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="min-h-full bg-background">
      <div className="max-w-3xl mx-auto py-8 px-4">
        <motion.div variants={staggerContainer} initial="hidden" animate="visible">
          <PageHeader
            title="Frequently Asked Questions"
            description="Answers curated from the most common questions asked by your team."
          />

          {/* Filters */}
          <motion.div variants={fadeInUp} className="flex gap-2 mb-4 flex-wrap">
            <div className="relative flex-1 min-w-48">
              <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
              <Input
                className="pl-8"
                placeholder="Search questions…"
                value={search}
                onChange={e => setSearch(e.target.value)}
              />
            </div>
            <Input
              className="w-36"
              placeholder="Product"
              value={product}
              onChange={e => { setProduct(e.target.value); setPage(0); }}
            />
            <Input
              className="w-28"
              placeholder="Version"
              value={version}
              onChange={e => { setVersion(e.target.value); setPage(0); }}
            />
          </motion.div>

          {/* Content */}
          {loading && (
            <div className="space-y-3">
              <SkeletonCard />
              <SkeletonCard />
              <SkeletonCard />
            </div>
          )}
          {error && <div className="text-danger text-sm py-4">{error}</div>}

          {!loading && filtered.length === 0 && (
            <EmptyState
              icon={MessageCircle}
              title="No FAQ entries yet"
              description="Questions your team asks frequently will appear here once reviewed."
            />
          )}

          {!loading && filtered.length > 0 && (
            <motion.div variants={fadeInUp} className="space-y-3">
              {filtered.map(entry => (
                <FaqCard
                  key={entry.id}
                  entry={entry}
                  expanded={expandedId === entry.id}
                  onToggle={() => setExpandedId(prev => prev === entry.id ? null : entry.id)}
                  onHelpful={() => handleHelpful(entry.id)}
                  canVote={!!token}
                />
              ))}
            </motion.div>
          )}

          {/* Pagination */}
          {data && data.totalPages > 1 && (
            <motion.div variants={fadeInUp} className="flex justify-center items-center gap-2 mt-6">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
              >
                Previous
              </Button>
              <span className="px-3 py-1.5 text-sm text-muted-foreground">
                {page + 1} / {data.totalPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(p => Math.min(data.totalPages - 1, p + 1))}
                disabled={page >= data.totalPages - 1}
              >
                Next
              </Button>
            </motion.div>
          )}
        </motion.div>
      </div>
    </div>
  );
}

function FaqCard({
  entry, expanded, onToggle, onHelpful, canVote,
}: {
  entry: FaqEntry;
  expanded: boolean;
  onToggle: () => void;
  onHelpful: () => void;
  canVote: boolean;
}) {
  return (
    <Card className="overflow-hidden">
      <button
        onClick={onToggle}
        className="flex items-start gap-3 w-full p-4 text-left hover:bg-surface-hover transition-colors"
      >
        <MessageCircle size={16} className="text-primary flex-shrink-0 mt-0.5" />
        <span className="flex-1 text-sm font-medium text-foreground">{entry.question}</span>
        <div className="flex items-center gap-2 flex-shrink-0">
          {(entry.product || entry.version) && (
            <Badge variant="primary">
              {entry.product}{entry.version ? ` ${entry.version}` : ''}
            </Badge>
          )}
          {expanded ? <ChevronUp size={14} className="text-muted-foreground" /> : <ChevronDown size={14} className="text-muted-foreground" />}
        </div>
      </button>

      {expanded && (
        <div className="px-4 pb-4 border-t border-border">
          <div className="pt-3 prose prose-sm max-w-none text-foreground prose-p:my-1.5 prose-pre:bg-muted prose-code:text-primary prose-code:bg-primary/10 prose-code:px-1 prose-code:rounded prose-code:before:content-none prose-code:after:content-none">
            <ReactMarkdown>{entry.answer}</ReactMarkdown>
          </div>
          <div className="flex items-center justify-between mt-3 pt-2 border-t border-border">
            <div className="flex items-center gap-3 text-xs text-muted-foreground">
              <span>{entry.viewCount} view{entry.viewCount !== 1 ? 's' : ''}</span>
              <span>{entry.helpfulCount} found helpful</span>
            </div>
            {canVote && (
              <Button
                variant="outline"
                size="sm"
                leftIcon={<ThumbsUp size={12} />}
                className="hover:bg-success/10 hover:text-success hover:border-success/20"
                onClick={onHelpful}
              >
                Helpful
              </Button>
            )}
          </div>
        </div>
      )}
    </Card>
  );
}
