import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { useAuth } from '../../context/AuthContext';
import { fetchCostSummary, CostSummary, DailyStat } from '../../services/analyticsService';
import { DollarSign, User, Package } from 'lucide-react';
import { Card, CardHeader } from '../ui/Card';
import EmptyState from '../ui/EmptyState';
import { SkeletonCard } from '../ui/Skeleton';
import { fadeInUp, staggerContainer, EASE_OUT } from '../../lib/motion';

function CostBarChart({ data }: { data: DailyStat[] }) {
  if (!data.length) return <EmptyState icon={DollarSign} title="No data yet" className="py-8" />;
  const max = Math.max(...data.map(d => d.estimatedCost), 0.001);
  return (
    <div className="h-28 flex items-end gap-px">
      {data.map((d, i) => (
        <div key={i} className="flex-1 flex flex-col items-center group relative">
          <motion.div
            className="w-full bg-primary rounded-sm opacity-80 hover:opacity-100 transition-opacity"
            initial={{ height: 0 }}
            animate={{ height: `${(d.estimatedCost / max) * 96}px` }}
            transition={{ duration: 0.3, ease: EASE_OUT }}
          />
          <div className="absolute -top-8 left-1/2 -translate-x-1/2 bg-foreground text-background text-[10px] px-1.5 py-0.5 rounded opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap pointer-events-none z-10">
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

  if (loading) {
    return (
      <div className="space-y-6">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <SkeletonCard /><SkeletonCard /><SkeletonCard />
        </div>
        <SkeletonCard className="h-40" />
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
          <SkeletonCard /><SkeletonCard />
        </div>
      </div>
    );
  }
  if (!cost) return <EmptyState icon={DollarSign} title="No cost data yet" description="Cost data will appear here once queries start generating LLM usage." />;

  return (
    <motion.div variants={staggerContainer} initial="hidden" animate="visible" className="space-y-6">
      {/* Summary cards */}
      <motion.div variants={fadeInUp} className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {[
          { label: 'Cost this month', value: `$${cost.totalCostThisMonth.toFixed(4)}`, sub: 'estimated LLM cost', icon: <DollarSign size={18} className="text-success" /> },
          { label: 'Avg cost / query', value: `$${cost.avgCostPerQuery.toFixed(5)}`, sub: 'last 30 days', icon: <DollarSign size={18} className="text-primary" /> },
          { label: 'Total cost (all time)', value: `$${cost.totalCostAllTime.toFixed(4)}`, sub: 'since deployment', icon: <DollarSign size={18} className="text-muted-foreground" /> },
        ].map(c => (
          <Card key={c.label} className="p-4">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-medium text-muted-foreground">{c.label}</span>
              {c.icon}
            </div>
            <div className="text-2xl font-bold text-foreground">{c.value}</div>
            <div className="text-xs text-muted-foreground mt-0.5">{c.sub}</div>
          </Card>
        ))}
      </motion.div>

      {/* Daily cost chart */}
      <motion.div variants={fadeInUp}>
        <Card className="p-5">
          <h3 className="text-sm font-semibold text-foreground mb-3">Daily Cost Trend (last 30 days)</h3>
          <CostBarChart data={cost.dailyCost} />
          <div className="flex justify-between text-[10px] text-muted-foreground mt-1">
            <span>{cost.dailyCost[0]?.date?.slice(5) ?? ''}</span>
            <span>{cost.dailyCost[cost.dailyCost.length - 1]?.date?.slice(5) ?? ''}</span>
          </div>
          <p className="text-xs text-muted-foreground mt-2">Estimated based on token usage × model pricing. Defaults: gpt-4o-mini ($0.15/1M input, $0.60/1M output).</p>
        </Card>
      </motion.div>

      {/* Cost by user / product */}
      <motion.div variants={fadeInUp} className="grid grid-cols-1 sm:grid-cols-2 gap-6">
        <Card>
          <CardHeader className="flex items-center gap-2">
            <User size={16} className="text-primary" />
            <h3 className="text-sm font-semibold text-foreground">Cost by User (top 10, this month)</h3>
          </CardHeader>
          {cost.costByUser.length === 0 ? (
            <EmptyState icon={User} title="No data" className="py-8" />
          ) : (
            <div className="divide-y divide-border">
              {cost.costByUser.map((u, i) => (
                <div key={i} className="px-5 py-3 flex items-center justify-between">
                  <div>
                    <div className="text-sm font-medium text-foreground">{u.username}</div>
                    <div className="text-xs text-muted-foreground">{u.queryCount} queries</div>
                  </div>
                  <span className="text-sm font-semibold text-success">{fmt(u.totalCost)}</span>
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* Cost by product */}
        <Card>
          <CardHeader className="flex items-center gap-2">
            <Package size={16} className="text-accent" />
            <h3 className="text-sm font-semibold text-foreground">Cost by Product (this month)</h3>
          </CardHeader>
          {cost.costByProduct.length === 0 ? (
            <EmptyState icon={Package} title="No data" className="py-8" />
          ) : (
            <div className="divide-y divide-border">
              {cost.costByProduct.map((p, i) => (
                <div key={i} className="px-5 py-3 flex items-center justify-between">
                  <div>
                    <div className="text-sm font-medium text-foreground">{p.product}</div>
                    <div className="text-xs text-muted-foreground">{p.version ?? 'all versions'} · {p.queryCount} queries</div>
                  </div>
                  <span className="text-sm font-semibold text-accent">{fmt(p.totalCost)}</span>
                </div>
              ))}
            </div>
          )}
        </Card>
      </motion.div>
    </motion.div>
  );
}
