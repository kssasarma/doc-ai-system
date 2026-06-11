import React, { useEffect, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import {
  fetchProductCoverage, fetchDocumentCoverage,
  ProductCoverage, DocumentCoverage,
} from '../../services/analyticsService';
import { FileText, AlertTriangle } from 'lucide-react';

function ConfidencePill({ value }: { value: number }) {
  const pct = Math.round(value * 100);
  const color = pct >= 80 ? 'bg-green-100 text-green-700'
    : pct >= 60 ? 'bg-yellow-100 text-yellow-700'
    : 'bg-red-100 text-red-700';
  return <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${color}`}>{pct}%</span>;
}

export default function CoverageTab() {
  const { token } = useAuth();
  const [products, setProducts] = useState<ProductCoverage[]>([]);
  const [docs, setDocs] = useState<DocumentCoverage[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!token) return;
    Promise.all([fetchProductCoverage(token), fetchDocumentCoverage(token)])
      .then(([p, d]) => {
        if (p.success && p.data) setProducts(p.data);
        if (d.success && d.data) setDocs(d.data);
      })
      .finally(() => setLoading(false));
  }, [token]);

  if (loading) return <div className="p-12 text-center text-gray-400">Loading coverage data…</div>;

  const maxCitations = Math.max(...docs.map(d => d.citationCount), 1);

  return (
    <div className="space-y-6">
      {/* Product Coverage */}
      <div className="bg-white rounded-xl border border-gray-200">
        <div className="px-5 py-4 border-b border-gray-100">
          <h3 className="text-sm font-semibold text-gray-700">Product Coverage by Query Volume</h3>
          <p className="text-xs text-gray-400 mt-0.5">Per product/version: query count, avg confidence, low-confidence rate</p>
        </div>
        {products.length === 0 ? (
          <div className="p-12 text-center text-gray-400 text-sm">No queries recorded yet.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wide">
                <tr>
                  <th className="px-5 py-3 text-left">Product</th>
                  <th className="px-5 py-3 text-left">Version</th>
                  <th className="px-5 py-3 text-right">Queries</th>
                  <th className="px-5 py-3 text-center">Avg Confidence</th>
                  <th className="px-5 py-3 text-right">Low Confidence</th>
                  <th className="px-5 py-3 text-right">Low Conf %</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {products.map((p, i) => (
                  <tr key={i} className="hover:bg-gray-50">
                    <td className="px-5 py-3 font-medium text-gray-900">{p.product}</td>
                    <td className="px-5 py-3 text-gray-500">{p.version ?? 'all'}</td>
                    <td className="px-5 py-3 text-right text-gray-700">{p.queryCount.toLocaleString()}</td>
                    <td className="px-5 py-3 text-center"><ConfidencePill value={p.avgConfidence} /></td>
                    <td className="px-5 py-3 text-right text-gray-700">{p.lowConfidenceCount.toLocaleString()}</td>
                    <td className="px-5 py-3 text-right">
                      <span className={`text-xs font-medium ${p.lowConfidencePct > 30 ? 'text-red-600' : p.lowConfidencePct > 15 ? 'text-yellow-600' : 'text-green-600'}`}>
                        {p.lowConfidencePct.toFixed(1)}%
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Documentation Heatmap */}
      <div className="bg-white rounded-xl border border-gray-200 p-5">
        <div className="flex items-center gap-2 mb-1">
          <FileText size={16} className="text-blue-500" />
          <h3 className="text-sm font-semibold text-gray-700">Documentation Citation Heatmap (last 30 days)</h3>
        </div>
        <p className="text-xs text-gray-400 mb-4">How often each document was cited as a source. Cold documents may be under-used or irrelevant.</p>
        {docs.length === 0 ? (
          <div className="text-gray-400 text-sm py-6 text-center">No citations logged yet.</div>
        ) : (
          <div className="space-y-2">
            {docs.map((d, i) => {
              const pct = (d.citationCount / maxCitations) * 100;
              const heat = pct > 75 ? 'bg-red-400' : pct > 50 ? 'bg-orange-400' : pct > 25 ? 'bg-yellow-400' : 'bg-blue-300';
              return (
                <div key={i} className="flex items-center gap-3">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between mb-0.5">
                      <span className="text-xs text-gray-700 truncate" title={d.documentName}>{d.documentName}</span>
                      <span className="text-xs text-gray-500 ml-2 flex-shrink-0">{d.citationCount}×</span>
                    </div>
                    <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
                      <div className={`h-full rounded-full ${heat}`} style={{ width: `${pct}%` }} />
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
        <div className="flex items-center gap-3 mt-4 text-xs text-gray-400">
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-sm bg-blue-300 inline-block" /> Low</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-sm bg-yellow-400 inline-block" /> Medium</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-sm bg-orange-400 inline-block" /> High</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-sm bg-red-400 inline-block" /> Very High</span>
        </div>
      </div>

      {/* Gap detector */}
      <div className="bg-white rounded-xl border border-gray-200 p-5">
        <div className="flex items-center gap-2 mb-1">
          <AlertTriangle size={16} className="text-orange-500" />
          <h3 className="text-sm font-semibold text-gray-700">Documentation Gap Signals</h3>
        </div>
        <p className="text-xs text-gray-400 mb-4">Products with &gt;15% low-confidence queries may have documentation gaps.</p>
        {products.filter(p => p.lowConfidencePct > 15).length === 0 ? (
          <div className="text-sm text-green-600 bg-green-50 rounded-lg px-4 py-3">
            No significant gaps detected. All products have &lt;15% low-confidence query rate.
          </div>
        ) : (
          <div className="space-y-2">
            {products.filter(p => p.lowConfidencePct > 15).map((p, i) => (
              <div key={i} className="flex items-center justify-between bg-orange-50 border border-orange-100 rounded-lg px-4 py-2.5">
                <div>
                  <span className="text-sm font-medium text-orange-800">{p.product}</span>
                  {p.version && <span className="text-xs text-orange-600 ml-1">v{p.version}</span>}
                  <p className="text-xs text-orange-600 mt-0.5">{p.lowConfidenceCount} of {p.queryCount} queries returned low-confidence answers</p>
                </div>
                <span className="text-lg font-bold text-orange-700">{p.lowConfidencePct.toFixed(0)}%</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
