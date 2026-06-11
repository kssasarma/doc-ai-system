import React, { useEffect, useState } from 'react';
import { Check, X, RefreshCw, MessageSquare, Zap } from 'lucide-react';
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

export default function FaqManagementTab() {
  const { token } = useAuth();
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
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-gray-800 mb-1">FAQ Review Queue</h2>
        <p className="text-sm text-gray-500">
          Auto-generated FAQ entries from your query logs. Approve to publish or reject to discard.
        </p>
      </div>

      {/* On-demand generation */}
      <div className="bg-purple-50 border border-purple-100 rounded-xl p-4">
        <div className="flex items-center gap-2 mb-2 text-sm font-medium text-purple-800">
          <Zap size={14} /> Generate FAQ from query logs
        </div>
        <div className="flex gap-2">
          <input
            className="flex-1 px-3 py-2 text-sm border border-purple-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-300 bg-white"
            placeholder="Product name"
            value={generateProduct}
            onChange={e => setGenerateProduct(e.target.value)}
          />
          <button
            onClick={handleGenerate}
            disabled={generating || !generateProduct.trim()}
            className="flex items-center gap-1.5 px-4 py-2 bg-purple-600 text-white text-sm rounded-lg hover:bg-purple-700 disabled:opacity-50 transition-colors"
          >
            {generating ? <RefreshCw size={14} className="animate-spin" /> : <Zap size={14} />}
            Generate
          </button>
        </div>
        {generateMsg && <p className="text-xs mt-2 text-purple-700">{generateMsg}</p>}
      </div>

      {/* Queue stats */}
      {data && (
        <div className="text-sm text-gray-500">
          {data.totalElements} entr{data.totalElements !== 1 ? 'ies' : 'y'} awaiting review
        </div>
      )}

      {loading && (
        <div className="flex justify-center py-12">
          <div className="animate-spin rounded-full h-7 w-7 border-b-2 border-purple-600" />
        </div>
      )}

      {!loading && data?.content.length === 0 && (
        <div className="text-center py-12 text-gray-400">
          <MessageSquare size={36} className="mx-auto mb-2 opacity-30" />
          <p className="font-medium">No entries pending review</p>
          <p className="text-sm mt-1">Run FAQ generation above or wait for the weekly scheduled job.</p>
        </div>
      )}

      <div className="space-y-4">
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
      </div>

      {data && data.totalPages > 1 && (
        <div className="flex justify-center gap-2">
          <button
            onClick={() => setPage(p => Math.max(0, p - 1))}
            disabled={page === 0}
            className="px-3 py-1.5 text-sm border border-gray-200 rounded-lg disabled:opacity-40"
          >Previous</button>
          <span className="px-3 py-1.5 text-sm text-gray-500">{page + 1} / {data.totalPages}</span>
          <button
            onClick={() => setPage(p => Math.min(data.totalPages - 1, p + 1))}
            disabled={page >= data.totalPages - 1}
            className="px-3 py-1.5 text-sm border border-gray-200 rounded-lg disabled:opacity-40"
          >Next</button>
        </div>
      )}
    </div>
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
    <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <div className="flex items-start gap-3 p-4">
        <button onClick={onToggle} className="flex-1 text-left">
          <p className="text-sm font-medium text-gray-800">{entry.question}</p>
          <div className="flex items-center gap-2 mt-1">
            {entry.product && (
              <span className="text-xs px-2 py-0.5 bg-blue-50 text-blue-600 rounded-full">
                {entry.product}{entry.version ? ` ${entry.version}` : ''}
              </span>
            )}
            <span className="text-xs text-gray-400">Click to preview answer</span>
          </div>
        </button>
        <div className="flex gap-2 flex-shrink-0">
          <button
            onClick={onApprove}
            disabled={pending}
            className="flex items-center gap-1 px-3 py-1.5 text-xs bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50 transition-colors"
          >
            <Check size={12} /> Approve
          </button>
          <button
            onClick={onReject}
            disabled={pending}
            className="flex items-center gap-1 px-3 py-1.5 text-xs border border-red-200 text-red-600 rounded-lg hover:bg-red-50 disabled:opacity-50 transition-colors"
          >
            <X size={12} /> Reject
          </button>
        </div>
      </div>
      {expanded && (
        <div className="px-4 pb-4 border-t border-gray-100 pt-3">
          <div className="prose prose-sm max-w-none text-gray-700 prose-p:my-1 prose-code:text-blue-600 prose-code:bg-blue-50 prose-code:px-1 prose-code:rounded prose-code:before:content-none prose-code:after:content-none">
            <ReactMarkdown>{entry.answer}</ReactMarkdown>
          </div>
        </div>
      )}
    </div>
  );
}
