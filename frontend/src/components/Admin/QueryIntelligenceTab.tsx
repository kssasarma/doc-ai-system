import React, { useEffect, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { fetchFailedQueries, fetchDailyStats, FailedQuery, DailyStat } from '../../services/analyticsService';
import { AlertCircle, TrendingDown } from 'lucide-react';

function QualityTrendChart({ data }: { data: DailyStat[] }) {
  if (!data.length) return <div className="h-28 flex items-center justify-center text-gray-400 text-xs">No data yet</div>;
  const max = 1.0;
  return (
    <div className="h-28 flex items-end gap-px">
      {data.map((d, i) => {
        const pct = d.avgConfidence;
        const h = pct * 96;
        const color = pct >= 0.8 ? 'bg-green-400' : pct >= 0.6 ? 'bg-yellow-400' : 'bg-red-400';
        return (
          <div key={i} className="flex-1 flex flex-col items-center group relative">
            <div className={`w-full ${color} rounded-sm opacity-80`} style={{ height: `${h}px` }} />
            <div className="absolute -top-8 left-1/2 -translate-x-1/2 bg-gray-800 text-white text-[10px] px-1.5 py-0.5 rounded opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap pointer-events-none z-10">
              {d.date.slice(5)}: {(pct * 100).toFixed(0)}%
            </div>
          </div>
        );
      })}
    </div>
  );
}

export default function QueryIntelligenceTab() {
  const { token } = useAuth();
  const [failed, setFailed] = useState<FailedQuery[]>([]);
  const [daily, setDaily] = useState<DailyStat[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!token) return;
    Promise.all([fetchFailedQueries(token, 20), fetchDailyStats(token, 30)])
      .then(([f, d]) => {
        if (f.success && f.data) setFailed(f.data);
        if (d.success && d.data) setDaily(d.data);
      })
      .finally(() => setLoading(false));
  }, [token]);

  if (loading) return <div className="p-12 text-center text-gray-400">Loading query intelligence…</div>;

  return (
    <div className="space-y-6">
      {/* Quality trend */}
      <div className="bg-white rounded-xl border border-gray-200 p-5">
        <h3 className="text-sm font-semibold text-gray-700 mb-1 flex items-center gap-2">
          <TrendingDown size={16} className="text-blue-500" />
          Answer Quality Trend (avg confidence per day)
        </h3>
        <p className="text-xs text-gray-400 mb-3">Green = high confidence, yellow = medium, red = low. Tracks whether quality is improving or degrading.</p>
        <QualityTrendChart data={daily} />
        <div className="flex justify-between text-[10px] text-gray-400 mt-1">
          <span>{daily[0]?.date?.slice(5) ?? ''}</span>
          <span>{daily[daily.length - 1]?.date?.slice(5) ?? ''}</span>
        </div>
        <div className="flex items-center gap-4 mt-3 text-xs text-gray-400">
          <span className="flex items-center gap-1"><span className="w-3 h-3 bg-green-400 rounded-sm" /> High (≥80%)</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 bg-yellow-400 rounded-sm" /> Medium (60–80%)</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 bg-red-400 rounded-sm" /> Low (&lt;60%)</span>
        </div>
      </div>

      {/* Failed queries */}
      <div className="bg-white rounded-xl border border-gray-200">
        <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between">
          <div>
            <h3 className="text-sm font-semibold text-gray-700 flex items-center gap-2">
              <AlertCircle size={16} className="text-red-500" />
              Failed Queries (last 30 days)
            </h3>
            <p className="text-xs text-gray-400 mt-0.5">Questions that returned low-confidence (&lt;60%) answers — documentation gaps</p>
          </div>
          <span className="text-sm text-gray-500">{failed.length} unique patterns</span>
        </div>
        {failed.length === 0 ? (
          <div className="p-12 text-center text-gray-400 text-sm">No low-confidence queries found. Great documentation coverage!</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wide">
                <tr>
                  <th className="px-5 py-3 text-left">#</th>
                  <th className="px-5 py-3 text-left">Question Pattern</th>
                  <th className="px-5 py-3 text-left">Product</th>
                  <th className="px-5 py-3 text-right">Occurrences</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {failed.map((q, i) => (
                  <tr key={i} className="hover:bg-gray-50">
                    <td className="px-5 py-3 text-gray-400 text-xs">{i + 1}</td>
                    <td className="px-5 py-3 text-gray-800 max-w-md">
                      <div className="truncate" title={q.questionPreview}>{q.questionPreview}</div>
                    </td>
                    <td className="px-5 py-3 text-gray-500 text-xs">
                      {q.product ? `${q.product}${q.version ? ` v${q.version}` : ''}` : '—'}
                    </td>
                    <td className="px-5 py-3 text-right">
                      <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-red-50 text-red-700">{q.count}×</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
