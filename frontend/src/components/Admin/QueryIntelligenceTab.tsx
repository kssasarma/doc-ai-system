import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { useAuth } from '../../context/AuthContext';
import { fetchFailedQueries, fetchDailyStats, FailedQuery, DailyStat } from '../../services/analyticsService';
import { AlertCircle, TrendingDown, CheckCircle2 } from 'lucide-react';
import { Card, CardHeader } from '../ui/Card';
import Badge from '../ui/Badge';
import EmptyState from '../ui/EmptyState';
import { SkeletonCard } from '../ui/Skeleton';
import PageHeader from '../ui/PageHeader';
import { fadeInUp, staggerContainer } from '../../lib/motion';

function QualityTrendChart({ data }: { data: DailyStat[] }) {
  if (!data.length) return <div className="h-28 flex items-center justify-center text-muted-foreground text-xs">No data yet</div>;
  return (
    <div className="h-28 flex items-end gap-px">
      {data.map((d, i) => {
        const pct = d.avgConfidence;
        const h = pct * 96;
        const color = pct >= 0.8 ? 'bg-success' : pct >= 0.6 ? 'bg-warning' : 'bg-danger';
        return (
          <div key={i} className="flex-1 flex flex-col items-center group relative">
            <div className={`w-full ${color} rounded-sm opacity-80`} style={{ height: `${h}px` }} />
            <div className="absolute -top-8 left-1/2 -translate-x-1/2 bg-foreground text-background text-[10px] px-1.5 py-0.5 rounded opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap pointer-events-none z-10">
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

  if (loading) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Query Intelligence"
          description="Answer quality trends and recurring low-confidence questions that point to documentation gaps."
        />
        <SkeletonCard />
        <SkeletonCard />
      </div>
    );
  }

  return (
    <motion.div variants={staggerContainer} initial="hidden" animate="visible" className="space-y-6">
      <PageHeader
        title="Query Intelligence"
        description="Answer quality trends and recurring low-confidence questions that point to documentation gaps."
      />

      {/* Quality trend */}
      <motion.div variants={fadeInUp}>
        <Card className="p-5">
          <h3 className="text-sm font-semibold text-foreground mb-1 flex items-center gap-2">
            <TrendingDown size={16} className="text-primary" />
            Answer Quality Trend (avg confidence per day)
          </h3>
          <p className="text-xs text-muted-foreground mb-3">Green = high confidence, yellow = medium, red = low. Tracks whether quality is improving or degrading.</p>
          <QualityTrendChart data={daily} />
          <div className="flex justify-between text-[10px] text-muted-foreground mt-1">
            <span>{daily[0]?.date?.slice(5) ?? ''}</span>
            <span>{daily[daily.length - 1]?.date?.slice(5) ?? ''}</span>
          </div>
          <div className="flex items-center gap-4 mt-3 text-xs text-muted-foreground">
            <span className="flex items-center gap-1"><span className="w-3 h-3 bg-success rounded-sm" /> High (≥80%)</span>
            <span className="flex items-center gap-1"><span className="w-3 h-3 bg-warning rounded-sm" /> Medium (60–80%)</span>
            <span className="flex items-center gap-1"><span className="w-3 h-3 bg-danger rounded-sm" /> Low (&lt;60%)</span>
          </div>
        </Card>
      </motion.div>

      {/* Failed queries */}
      <motion.div variants={fadeInUp}>
        <Card>
          <CardHeader className="flex items-center justify-between">
            <div>
              <h3 className="text-sm font-semibold text-foreground flex items-center gap-2">
                <AlertCircle size={16} className="text-danger" />
                Failed Queries (last 30 days)
              </h3>
              <p className="text-xs text-muted-foreground mt-0.5">Questions that returned low-confidence (&lt;60%) answers — documentation gaps</p>
            </div>
            <span className="text-sm text-muted-foreground">{failed.length} unique patterns</span>
          </CardHeader>
          {failed.length === 0 ? (
            <EmptyState
              icon={CheckCircle2}
              title="No low-confidence queries found"
              description="Great documentation coverage! Failed query patterns will show up here if they occur."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-muted text-muted-foreground text-xs uppercase tracking-wide">
                  <tr>
                    <th className="px-5 py-3 text-left">#</th>
                    <th className="px-5 py-3 text-left">Question Pattern</th>
                    <th className="px-5 py-3 text-left">Product</th>
                    <th className="px-5 py-3 text-right">Occurrences</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {failed.map((q, i) => (
                    <tr key={i} className="hover:bg-surface-hover">
                      <td className="px-5 py-3 text-muted-foreground text-xs">{i + 1}</td>
                      <td className="px-5 py-3 text-foreground max-w-md">
                        <div className="truncate" title={q.questionPreview}>{q.questionPreview}</div>
                      </td>
                      <td className="px-5 py-3 text-muted-foreground text-xs">
                        {q.product ? `${q.product}${q.version ? ` v${q.version}` : ''}` : '—'}
                      </td>
                      <td className="px-5 py-3 text-right">
                        <Badge variant="danger">{q.count}×</Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </motion.div>
    </motion.div>
  );
}
