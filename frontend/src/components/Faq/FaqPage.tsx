import React, { useEffect, useState } from 'react';
import { MessageCircle, ThumbsUp, ChevronDown, ChevronUp, Search, BookOpen } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import { listApprovedFaq, markHelpful, type FaqEntry, type PagedFaqEntries } from '../../services/faqService';
import { useAuth } from '../../context/AuthContext';

export default function FaqPage() {
  const { token } = useAuth();
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
  };

  const filtered = (data?.content ?? []).filter(e =>
    !search || e.question.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-3xl mx-auto py-8 px-4">
        {/* Header */}
        <div className="mb-6">
          <div className="flex items-center gap-2 mb-1">
            <BookOpen size={22} className="text-blue-600" />
            <h1 className="text-2xl font-bold text-gray-900">Frequently Asked Questions</h1>
          </div>
          <p className="text-gray-500 text-sm">Answers curated from the most common questions asked by your team.</p>
        </div>

        {/* Filters */}
        <div className="flex gap-2 mb-4 flex-wrap">
          <div className="relative flex-1 min-w-48">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              className="w-full pl-8 pr-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-300"
              placeholder="Search questions…"
              value={search}
              onChange={e => setSearch(e.target.value)}
            />
          </div>
          <input
            className="px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-300 w-36"
            placeholder="Product"
            value={product}
            onChange={e => { setProduct(e.target.value); setPage(0); }}
          />
          <input
            className="px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-300 w-28"
            placeholder="Version"
            value={version}
            onChange={e => { setVersion(e.target.value); setPage(0); }}
          />
        </div>

        {/* Content */}
        {loading && (
          <div className="flex justify-center py-16">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600" />
          </div>
        )}
        {error && <div className="text-red-500 text-sm py-4">{error}</div>}

        {!loading && filtered.length === 0 && (
          <div className="text-center py-16 text-gray-400">
            <MessageCircle size={40} className="mx-auto mb-3 opacity-30" />
            <p className="font-medium">No FAQ entries yet</p>
            <p className="text-sm mt-1">Questions your team asks frequently will appear here once reviewed.</p>
          </div>
        )}

        <div className="space-y-3">
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
        </div>

        {/* Pagination */}
        {data && data.totalPages > 1 && (
          <div className="flex justify-center gap-2 mt-6">
            <button
              onClick={() => setPage(p => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-3 py-1.5 text-sm border border-gray-200 rounded-lg disabled:opacity-40 hover:bg-gray-50"
            >
              Previous
            </button>
            <span className="px-3 py-1.5 text-sm text-gray-500">
              {page + 1} / {data.totalPages}
            </span>
            <button
              onClick={() => setPage(p => Math.min(data.totalPages - 1, p + 1))}
              disabled={page >= data.totalPages - 1}
              className="px-3 py-1.5 text-sm border border-gray-200 rounded-lg disabled:opacity-40 hover:bg-gray-50"
            >
              Next
            </button>
          </div>
        )}
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
    <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <button
        onClick={onToggle}
        className="flex items-start gap-3 w-full p-4 text-left hover:bg-gray-50 transition-colors"
      >
        <MessageCircle size={16} className="text-blue-500 flex-shrink-0 mt-0.5" />
        <span className="flex-1 text-sm font-medium text-gray-800">{entry.question}</span>
        <div className="flex items-center gap-2 flex-shrink-0">
          {(entry.product || entry.version) && (
            <span className="text-xs px-2 py-0.5 bg-blue-50 text-blue-600 rounded-full">
              {entry.product}{entry.version ? ` ${entry.version}` : ''}
            </span>
          )}
          {expanded ? <ChevronUp size={14} className="text-gray-400" /> : <ChevronDown size={14} className="text-gray-400" />}
        </div>
      </button>

      {expanded && (
        <div className="px-4 pb-4 border-t border-gray-100">
          <div className="pt-3 prose prose-sm max-w-none text-gray-700 prose-p:my-1.5 prose-pre:bg-gray-100 prose-pre:rounded prose-code:text-blue-600 prose-code:bg-blue-50 prose-code:px-1 prose-code:rounded prose-code:before:content-none prose-code:after:content-none">
            <ReactMarkdown>{entry.answer}</ReactMarkdown>
          </div>
          <div className="flex items-center justify-between mt-3 pt-2 border-t border-gray-100">
            <div className="flex items-center gap-3 text-xs text-gray-400">
              <span>{entry.viewCount} view{entry.viewCount !== 1 ? 's' : ''}</span>
              <span>{entry.helpfulCount} found helpful</span>
            </div>
            {canVote && (
              <button
                onClick={onHelpful}
                className="flex items-center gap-1.5 px-3 py-1 text-xs rounded-lg border border-gray-200 text-gray-600 hover:bg-green-50 hover:border-green-200 hover:text-green-700 transition-colors"
              >
                <ThumbsUp size={12} /> Helpful
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
