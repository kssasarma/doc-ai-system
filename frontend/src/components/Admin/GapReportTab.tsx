import React, { useEffect, useState } from 'react';
import { AlertTriangle, Download, RefreshCw, ChevronDown, ChevronUp } from 'lucide-react';
import {
  listGapReports,
  generateGapReport,
  exportGapReport,
  type GapReport,
} from '../../services/gapReportService';
import { useAuth } from '../../context/AuthContext';

export default function GapReportTab() {
  const { token } = useAuth();
  const [reports, setReports] = useState<GapReport[]>([]);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [product, setProduct] = useState('');
  const [version, setVersion] = useState('');
  const [msg, setMsg] = useState<string | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  useEffect(() => { load(); }, []);

  const load = async () => {
    if (!token) return;
    setLoading(true);
    const result = await listGapReports(token);
    if (result.success && result.data) setReports(result.data);
    setLoading(false);
  };

  const handleGenerate = async () => {
    if (!token) return;
    setGenerating(true);
    setMsg(null);
    const result = await generateGapReport(token, product.trim() || undefined, version.trim() || undefined);
    if (result.success) {
      setMsg(result.data ? `Report generated with ${result.data.totalLowConfidenceQueries} queries analysed.` : 'No queries found for that period.');
      load();
    } else {
      setMsg(result.error ?? 'Generation failed');
    }
    setGenerating(false);
  };

  const handleExport = async (id: string) => {
    if (!token) return;
    try { await exportGapReport(id, token); }
    catch (e) { console.error(e); }
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-gray-800 mb-1">Documentation Gap Reports</h2>
        <p className="text-sm text-gray-500">
          AI-generated reports identifying topics with insufficient documentation coverage based on query failure analysis.
        </p>
      </div>

      {/* Generate */}
      <div className="bg-orange-50 border border-orange-100 rounded-xl p-4">
        <div className="flex items-center gap-2 mb-2 text-sm font-medium text-orange-800">
          <AlertTriangle size={14} /> Generate gap report
        </div>
        <div className="flex gap-2">
          <input
            className="flex-1 px-3 py-2 text-sm border border-orange-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-orange-300 bg-white"
            placeholder="Product (optional — all if blank)"
            value={product}
            onChange={e => setProduct(e.target.value)}
          />
          <input
            className="w-32 px-3 py-2 text-sm border border-orange-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-orange-300 bg-white"
            placeholder="Version"
            value={version}
            onChange={e => setVersion(e.target.value)}
          />
          <button
            onClick={handleGenerate}
            disabled={generating}
            className="flex items-center gap-1.5 px-4 py-2 bg-orange-600 text-white text-sm rounded-lg hover:bg-orange-700 disabled:opacity-50 transition-colors"
          >
            {generating ? <RefreshCw size={14} className="animate-spin" /> : <AlertTriangle size={14} />}
            Generate
          </button>
        </div>
        {msg && <p className="text-xs mt-2 text-orange-700">{msg}</p>}
      </div>

      {loading && (
        <div className="flex justify-center py-12">
          <div className="animate-spin rounded-full h-7 w-7 border-b-2 border-orange-600" />
        </div>
      )}

      {!loading && reports.length === 0 && (
        <div className="text-center py-12 text-gray-400">
          <AlertTriangle size={36} className="mx-auto mb-2 opacity-30" />
          <p className="font-medium">No gap reports yet</p>
          <p className="text-sm mt-1">Generate your first report above or wait for the monthly scheduled job.</p>
        </div>
      )}

      <div className="space-y-4">
        {reports.map(report => (
          <GapReportCard
            key={report.id}
            report={report}
            expanded={expandedId === report.id}
            onToggle={() => setExpandedId(prev => prev === report.id ? null : report.id)}
            onExport={() => handleExport(report.id)}
          />
        ))}
      </div>
    </div>
  );
}

function GapReportCard({
  report, expanded, onToggle, onExport,
}: {
  report: GapReport;
  expanded: boolean;
  onToggle: () => void;
  onExport: () => void;
}) {
  let topics: Array<{ topic: string; queryCount: number; uniqueUsers: number; exampleQuestions: string[]; suggestedDocStub: string }> = [];
  try { topics = JSON.parse(report.gapTopics); } catch { /* ignore */ }

  return (
    <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <div className="flex items-center gap-3 p-4">
        <button onClick={onToggle} className="flex-1 text-left">
          <div className="flex items-center gap-2 mb-0.5">
            {report.product && (
              <span className="text-xs px-2 py-0.5 bg-blue-50 text-blue-600 rounded-full">
                {report.product}{report.version ? ` ${report.version}` : ''}
              </span>
            )}
            <span className="text-xs text-gray-400">
              {report.reportPeriodStart} → {report.reportPeriodEnd}
            </span>
          </div>
          <div className="flex items-center gap-3 text-sm text-gray-700">
            <span className="font-medium">{topics.length} gap{topics.length !== 1 ? 's' : ''} identified</span>
            <span className="text-gray-400">·</span>
            <span className="text-gray-500">{report.totalLowConfidenceQueries} low-confidence queries</span>
          </div>
        </button>
        <div className="flex gap-2 flex-shrink-0">
          <button
            onClick={onExport}
            className="flex items-center gap-1 px-3 py-1.5 text-xs border border-gray-200 text-gray-600 rounded-lg hover:bg-gray-50 transition-colors"
            title="Export as Markdown"
          >
            <Download size={12} /> Export
          </button>
          <button onClick={onToggle} className="p-1.5 text-gray-400 hover:text-gray-600">
            {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
          </button>
        </div>
      </div>

      {expanded && topics.length > 0 && (
        <div className="border-t border-gray-100 divide-y divide-gray-50">
          {topics.map((t, i) => (
            <div key={i} className="px-4 py-3">
              <div className="flex items-start justify-between gap-3 mb-1">
                <p className="text-sm font-medium text-gray-800">{t.topic}</p>
                <div className="flex gap-2 flex-shrink-0 text-xs text-gray-400">
                  <span>{t.queryCount} queries</span>
                  <span>·</span>
                  <span>{t.uniqueUsers} user{t.uniqueUsers !== 1 ? 's' : ''}</span>
                </div>
              </div>
              {t.exampleQuestions?.length > 0 && (
                <div className="mb-2">
                  <p className="text-xs text-gray-400 mb-1">Example questions:</p>
                  <ul className="text-xs text-gray-600 space-y-0.5">
                    {t.exampleQuestions.slice(0, 3).map((q, j) => (
                      <li key={j} className="truncate">• {q}</li>
                    ))}
                  </ul>
                </div>
              )}
              {t.suggestedDocStub && (
                <div className="bg-orange-50 border border-orange-100 rounded-lg p-2.5">
                  <p className="text-xs font-medium text-orange-700 mb-1">Suggested documentation stub:</p>
                  <p className="text-xs text-orange-600 leading-relaxed line-clamp-4">{t.suggestedDocStub}</p>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
