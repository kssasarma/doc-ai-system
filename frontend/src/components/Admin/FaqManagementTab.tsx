import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { Check, X, MessageSquare, Zap } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import {
  listPendingFaq,
  approveFaqEntry,
  rejectFaqEntry,
  triggerFaqGeneration,
  type FaqEntry,
  type PagedFaqEntries,
} from '../../services/faqService';
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

export default function FaqManagementTab() {
  const { token } = useAuth();
  const toast = useToast();
  const [data, setData] = useState<PagedFaqEntries | null>(null);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [actionPending, setActionPending] = useState<string | null>(null);
  const [generateProduct, setGenerateProduct] = useState('');
  const [generating, setGenerating] = useState(false);
  const [generateMsg, setGenerateMsg] = useState<string | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  useEffect(() => { load(); }, [page]);

  const load = async () => {
    if (!token) return;
    setLoading(true);
    const result = await listPendingFaq(token, page, 10);
    if (result.success && result.data) setData(result.data);
    setLoading(false);
  };

  const handleApprove = async (id: string) => {
    if (!token) return;
    setActionPending(id);
    const result = await approveFaqEntry(id, token);
    if (result.success) {
      setData(prev => prev ? {
        ...prev,
        content: prev.content.filter(e => e.id !== id),
        totalElements: prev.totalElements - 1,
      } : prev);
      toast.success('FAQ entry approved and published.');
    } else {
      toast.error(result.error ?? 'Failed to approve entry');
    }
    setActionPending(null);
  };

  const handleReject = async (id: string) => {
    if (!token) return;
    setActionPending(id);
    const result = await rejectFaqEntry(id, token);
    if (result.success) {
      setData(prev => prev ? {
        ...prev,
        content: prev.content.filter(e => e.id !== id),
        totalElements: prev.totalElements - 1,
      } : prev);
      toast.success('FAQ entry rejected.');
    } else {
      toast.error(result.error ?? 'Failed to reject entry');
    }
    setActionPending(null);
  };

  const handleGenerate = async () => {
    if (!token || !generateProduct.trim()) return;
    setGenerating(true);
    setGenerateMsg(null);
    const result = await triggerFaqGeneration(token, generateProduct.trim());
    if (result.success && result.data) {
      setGenerateMsg(`Generated ${result.data.entriesGenerated} new FAQ entries for review.`);
      load();
    } else {
      setGenerateMsg(result.error ?? 'Generation failed');
    }
    setGenerating(false);
  };

  return (
    <motion.div variants={staggerContainer} initial="hidden" animate="visible">
      <PageHeader
        title="FAQ Review Queue"
        description="Auto-generated FAQ entries from your query logs. Approve to publish or reject to discard."
      />

      <div className="space-y-6">
        {/* On-demand generation */}
        <motion.div variants={fadeInUp}>
          <Card className="p-4 bg-accent/10 border-accent/20">
            <div className="flex items-center gap-2 mb-2 text-sm font-medium text-accent">
              <Zap size={14} /> Generate FAQ from query logs
            </div>
            <div className="flex gap-2">
              <Input
                className="flex-1"
                aria-label="Product name"
                placeholder="Product name"
                value={generateProduct}
                onChange={e => setGenerateProduct(e.target.value)}
              />
              <Button
                variant="primary"
                onClick={handleGenerate}
                disabled={generating || !generateProduct.trim()}
                loading={generating}
                leftIcon={!generating ? <Zap size={14} /> : undefined}
              >
                Generate
              </Button>
            </div>
            {generateMsg && <p className="text-xs mt-2 text-accent">{generateMsg}</p>}
          </Card>
        </motion.div>

        {/* Queue stats */}
        {data && (
          <motion.div variants={fadeInUp} className="text-sm text-muted-foreground">
            {data.totalElements} entr{data.totalElements !== 1 ? 'ies' : 'y'} awaiting review
          </motion.div>
        )}

        {loading && (
          <motion.div variants={fadeInUp} className="space-y-4">
            {[0, 1, 2].map(i => <SkeletonCard key={i} />)}
          </motion.div>
        )}

        {!loading && data?.content.length === 0 && (
          <motion.div variants={fadeInUp}>
            <EmptyState
              icon={MessageSquare}
              title="No entries pending review"
              description="Run FAQ generation above or wait for the weekly scheduled job."
            />
          </motion.div>
        )}

        <motion.div variants={fadeInUp} className="space-y-4">
          {data?.content.map(entry => (
            <FaqReviewCard
              key={entry.id}
              entry={entry}
              expanded={expandedId === entry.id}
              onToggle={() => setExpandedId(prev => prev === entry.id ? null : entry.id)}
              onApprove={() => handleApprove(entry.id)}
              onReject={() => handleReject(entry.id)}
              pending={actionPending === entry.id}
            />
          ))}
        </motion.div>

        {data && data.totalPages > 1 && (
          <motion.div variants={fadeInUp} className="flex justify-center items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage(p => Math.max(0, p - 1))}
              disabled={page === 0}
            >Previous</Button>
            <span className="px-3 py-1.5 text-sm text-muted-foreground">{page + 1} / {data.totalPages}</span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage(p => Math.min(data.totalPages - 1, p + 1))}
              disabled={page >= data.totalPages - 1}
            >Next</Button>
          </motion.div>
        )}
      </div>
    </motion.div>
  );
}

function FaqReviewCard({
  entry, expanded, onToggle, onApprove, onReject, pending,
}: {
  entry: FaqEntry;
  expanded: boolean;
  onToggle: () => void;
  onApprove: () => void;
  onReject: () => void;
  pending: boolean;
}) {
  return (
    <Card className="overflow-hidden">
      <div className="flex items-start gap-3 p-4">
        <button onClick={onToggle} className="flex-1 text-left">
          <p className="text-sm font-medium text-foreground">{entry.question}</p>
          <div className="flex items-center gap-2 mt-1">
            {entry.product && (
              <Badge variant="primary">
                {entry.product}{entry.version ? ` ${entry.version}` : ''}
              </Badge>
            )}
            <span className="text-xs text-muted-foreground">Click to preview answer</span>
          </div>
        </button>
        <div className="flex gap-2 flex-shrink-0">
          <Button
            variant="primary"
            size="sm"
            onClick={onApprove}
            disabled={pending}
            leftIcon={<Check size={12} />}
          >
            Approve
          </Button>
          <Button
            variant="danger"
            size="sm"
            onClick={onReject}
            disabled={pending}
            leftIcon={<X size={12} />}
          >
            Reject
          </Button>
        </div>
      </div>
      {expanded && (
        <div className="px-4 pb-4 border-t border-border pt-3">
          <div className="prose prose-sm dark:prose-invert max-w-none text-foreground prose-p:my-1 prose-code:text-primary prose-code:bg-muted prose-code:px-1 prose-code:rounded prose-code:before:content-none prose-code:after:content-none">
            <ReactMarkdown>{entry.answer}</ReactMarkdown>
          </div>
        </div>
      )}
    </Card>
  );
}
