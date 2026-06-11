import React, { useEffect, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { fetchCostSummary, CostSummary, DailyStat } from '../../services/analyticsService';
import { DollarSign, User, Package } from 'lucide-react';

function CostBarChart({ data }: { data: DailyStat[] }) {
  if (!data.length) return <div className="h-28 flex items-center justify-center text-gray-400 text-xs">No data yet</div>;
  const max = Math.max(...data.map(d => d.estimatedCost), 0.001);
  return (
    <div className="h-28 flex items-end gap-px">
      {data.map((d, i) => (
        <div key={i} className="flex-1 flex flex-col items-center group relative">
          <div
            className="w-full bg-emerald-500 rounded-sm opacity-80 hover:opacity-100 transition-opacity"
            style={{ height: `${(d.estimatedCost / max) * 96}px` }}
          />
          <div className="absolute -top-8 left-1/2 -translate-x-1/2 bg-gray-800 text-white text-[10px] px-1.5 py-0.5 rounded opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap pointer-events-none z-10">
            {d.date.slice(5)}: ${d.estimatedCost.toFixed(4)}
          </div>
        </div>
      ))}
    </div>
  );
}

function fmt(n: number) {
  return n < 0.01 ? `$${(n * 1000).toFixed(2)}m` : `$${n.toFixed(4)}`;
}

export default function CostTrackingTab() {
  const { token } = useAuth();
  const [cost, setCost] = useState<CostSummary | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!token) return;
    fetchCostSummary(token).then(res => {
      if (res.success && res.data) setCost(res.data);
    }).finally(() => setLoading(false));
  }, [token]);

  if (loading) return <div className="p-12 text-center text-gray-400">Loading cost data…</div>;
  if (!cost) return <div className="p-12 text-center text-gray-400">No cost data yet.</div>;

  return (
    <div className="space-y-6">
      {/* Summary cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {[
          { label: 'Cost this month', value: `$${cost.totalCostThisMonth.toFixed(4)}`, sub: 'estimated LLM cost', icon: <DollarSign size={18} className="text-emerald-500" /> },
          { label: 'Avg cost / query', value: `$${cost.avgCostPerQuery.toFixed(5)}`, sub: 'last 30 days', icon: <DollarSign size={18} className="text-blue-500" /> },
          { label: 'Total cost (all time)', value: `$${cost.totalCostAllTime.toFixed(4)}`, sub: 'since deployment', icon: <DollarSign size={18} className="text-gray-400" /> },
        ].map(c => (
          <div key={c.label} className="bg-white rounded-xl border border-gray-200 p-4">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-medium text-gray-500">{c.label}</span>
              {c.icon}
            </div>
            <div className="text-2xl font-bold text-gray-900">{c.value}</div>
            <div className="text-xs text-gray-400 mt-0.5">{c.sub}</div>
          </div>
        ))}
      </div>

      {/* Daily cost chart */}
      <div className="bg-white rounded-xl border border-gray-200 p-5">
        <h3 className="text-sm font-semibold text-gray-700 mb-3">Daily Cost Trend (last 30 days)</h3>
        <CostBarChart data={cost.dailyCost} />
        <div className="flex justify-between text-[10px] text-gray-400 mt-1">
          <span>{cost.dailyCost[0]?.date?.slice(5) ?? ''}</span>
          <span>{cost.dailyCost[cost.dailyCost.length - 1]?.date?.slice(5) ?? ''}</span>
        </div>
        <p className="text-xs text-gray-400 mt-2">Estimated based on token usage × model pricing. Defaults: gpt-4o-mini ($0.15/1M input, $0.60/1M output).</p>
      </div>

      {/* Cost by user */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl border border-gray-200">
          <div className="px-5 py-4 border-b border-gray-100 flex items-center gap-2">
            <User size={16} className="text-blue-500" />
            <h3 className="text-sm font-semibold text-gray-700">Cost by User (top 10, this month)</h3>
          </div>
          {cost.costByUser.length === 0 ? (
            <div className="p-6 text-center text-gray-400 text-sm">No data.</div>
          ) : (
            <div className="divide-y divide-gray-100">
              {cost.costByUser.map((u, i) => (
                <div key={i} className="px-5 py-3 flex items-center justify-between">
                  <div>
                    <div className="text-sm font-medium text-gray-800">{u.username}</div>
                    <div className="text-xs text-gray-400">{u.queryCount} queries</div>
                  </div>
                  <span className="text-sm font-semibold text-emerald-600">{fmt(u.totalCost)}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Cost by product */}
        <div className="bg-white rounded-xl border border-gray-200">
          <div className="px-5 py-4 border-b border-gray-100 flex items-center gap-2">
            <Package size={16} className="text-purple-500" />
            <h3 className="text-sm font-semibold text-gray-700">Cost by Product (this month)</h3>
          </div>
          {cost.costByProduct.length === 0 ? (
            <div className="p-6 text-center text-gray-400 text-sm">No data.</div>
          ) : (
            <div className="divide-y divide-gray-100">
              {cost.costByProduct.map((p, i) => (
                <div key={i} className="px-5 py-3 flex items-center justify-between">
                  <div>
                    <div className="text-sm font-medium text-gray-800">{p.product}</div>
                    <div className="text-xs text-gray-400">{p.version ?? 'all versions'} · {p.queryCount} queries</div>
                  </div>
                  <span className="text-sm font-semibold text-purple-600">{fmt(p.totalCost)}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
