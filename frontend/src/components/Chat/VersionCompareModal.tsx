import React, { useState } from 'react';
import { X, GitCompare, History, Loader2, AlertTriangle } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import {
  getVersionDiff, getEvolutionTimeline,
  VersionDiffResult, EvolutionTimeline,
} from '../../services/intelligenceService';

interface VersionCompareModalProps {
  product: string;
  versions: string[];
  onClose: () => void;
}

type Mode = 'diff' | 'evolution';
type Tone = 'green' | 'yellow' | 'red' | 'purple';

const TONE_CLASSES: Record<Tone, string> = {
  green: 'border-green-200 bg-green-50 text-green-800',
  yellow: 'border-yellow-200 bg-yellow-50 text-yellow-800',
  red: 'border-red-200 bg-red-50 text-red-800',
  purple: 'border-purple-200 bg-purple-50 text-purple-800',
};

function DiffSection({ label, content, tone }: { label: string; content: string | null; tone: Tone }) {
  if (!content || content.trim().toLowerCase() === 'none') return null;
  return (
    <div className={`border rounded-lg p-3 ${TONE_CLASSES[tone]}`}>
      <h4 className="text-xs font-semibold uppercase tracking-wide mb-1">{label}</h4>
      <p className="text-sm leading-relaxed whitespace-pre-wrap">{content}</p>
    </div>
  );
}

export default function VersionCompareModal({ product, versions, onClose }: VersionCompareModalProps) {
  const { token } = useAuth();
  const [mode, setMode] = useState<Mode>('diff');

  const [topic, setTopic] = useState('');
  const [versionA, setVersionA] = useState(versions[0] ?? '');
  const [versionB, setVersionB] = useState(versions[1] ?? versions[0] ?? '');
  const [diffResult, setDiffResult] = useState<VersionDiffResult | null>(null);

  const [question, setQuestion] = useState('');
  const [timeline, setTimeline] = useState<EvolutionTimeline | null>(null);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const runDiff = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || !topic.trim()) return;
    setLoading(true);
    setError('');
    setDiffResult(null);
    const res = await getVersionDiff(token, topic.trim(), product, versionA, versionB);
    if (res.success && res.data) setDiffResult(res.data);
    else setError(res.error ?? 'Failed to compare versions');
    setLoading(false);
  };

  const runEvolution = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || !question.trim()) return;
    setLoading(true);
    setError('');
    setTimeline(null);
    const res = await getEvolutionTimeline(token, question.trim(), product);
    if (res.success && res.data) setTimeline(res.data);
    else setError(res.error ?? 'Failed to build evolution timeline');
    setLoading(false);
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl max-h-[85vh] flex flex-col">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 flex-shrink-0">
          <div className="flex items-center gap-2 min-w-0">
            <GitCompare size={18} className="text-blue-600 flex-shrink-0" />
            <h2 className="text-lg font-semibold text-gray-900 truncate">{product} — Version Intelligence</h2>
          </div>
          <button onClick={onClose} className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors flex-shrink-0">
            <X size={16} />
          </button>
        </div>

        <div className="flex border-b border-gray-100 flex-shrink-0">
          <button
            onClick={() => setMode('diff')}
            className={`flex items-center gap-1.5 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
              mode === 'diff' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-800'
            }`}
          >
            <GitCompare size={14} /> Compare two versions
          </button>
          <button
            onClick={() => setMode('evolution')}
            className={`flex items-center gap-1.5 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
              mode === 'evolution' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-800'
            }`}
          >
            <History size={14} /> Evolution timeline
          </button>
        </div>

        <div className="overflow-y-auto flex-1 p-6">
          {mode === 'diff' ? (
            <>
              <form onSubmit={runDiff} className="space-y-3 mb-5">
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Topic</label>
                  <input
                    value={topic} onChange={e => setTopic(e.target.value)} required autoFocus
                    placeholder="e.g. SSO configuration"
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">From version</label>
                    <select value={versionA} onChange={e => setVersionA(e.target.value)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                      {versions.map(v => <option key={v} value={v}>{v}</option>)}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">To version</label>
                    <select value={versionB} onChange={e => setVersionB(e.target.value)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                      {versions.map(v => <option key={v} value={v}>{v}</option>)}
                    </select>
                  </div>
                </div>
                <button
                  type="submit" disabled={loading || !topic.trim()}
                  className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50 transition-colors"
                >
                  {loading ? <Loader2 size={14} className="animate-spin" /> : <GitCompare size={14} />}
                  {loading ? 'Comparing…' : 'Compare'}
                </button>
              </form>

              {error && (
                <div className="flex items-center gap-2 text-sm text-red-600 bg-red-50 rounded-lg px-3 py-2 mb-4">
                  <AlertTriangle size={14} className="flex-shrink-0" />{error}
                </div>
              )}

              {diffResult && (
                <div className="space-y-3">
                  <p className="text-sm text-gray-700 bg-blue-50 border border-blue-100 rounded-lg p-3">{diffResult.summary}</p>
                  <DiffSection label="Added" content={diffResult.added} tone="green" />
                  <DiffSection label="Modified" content={diffResult.modified} tone="yellow" />
                  <DiffSection label="Removed" content={diffResult.removed} tone="red" />
                  <DiffSection label="Breaking changes" content={diffResult.breakingChanges} tone="purple" />
                </div>
              )}
            </>
          ) : (
            <>
              <form onSubmit={runEvolution} className="space-y-3 mb-5">
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Question</label>
                  <input
                    value={question} onChange={e => setQuestion(e.target.value)} required autoFocus
                    placeholder="e.g. How do I configure SSO?"
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
                <button
                  type="submit" disabled={loading || !question.trim()}
                  className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50 transition-colors"
                >
                  {loading ? <Loader2 size={14} className="animate-spin" /> : <History size={14} />}
                  {loading ? 'Building timeline…' : `Show across all ${versions.length} versions`}
                </button>
              </form>

              {error && (
                <div className="flex items-center gap-2 text-sm text-red-600 bg-red-50 rounded-lg px-3 py-2 mb-4">
                  <AlertTriangle size={14} className="flex-shrink-0" />{error}
                </div>
              )}

              {timeline && (
                <div className="space-y-3">
                  {timeline.breakingSummary && (
                    <p className="text-sm text-purple-800 bg-purple-50 border border-purple-100 rounded-lg p-3">
                      {timeline.breakingSummary}
                    </p>
                  )}
                  {timeline.snapshots.map(snap => (
                    <div key={snap.version} className="border border-gray-200 rounded-lg p-3">
                      <div className="flex items-center justify-between mb-1.5">
                        <span className="text-sm font-semibold text-gray-800">{snap.version}</span>
                        {snap.hasDocumentation ? (
                          <span className="text-xs text-gray-400">{Math.round(snap.confidence * 100)}% match</span>
                        ) : (
                          <span className="text-xs text-gray-400 italic">No documentation found</span>
                        )}
                      </div>
                      {snap.answer && <p className="text-sm text-gray-600 leading-relaxed">{snap.answer}</p>}
                    </div>
                  ))}
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
