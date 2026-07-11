import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { useAuth } from '../../context/AuthContext';
import {
  fetchOverview, fetchDailyStats, fetchTopQuestions,
  OverviewStats, DailyStat, TopQuestion,
} from '../../services/analyticsService';
import { TrendingUp, Users, MessageSquare, ThumbsUp, ThumbsDown, BarChart2 } from 'lucide-react';
import { fadeInUp, staggerContainer } from '../../lib/motion';
import PageHeader from '../ui/PageHeader';
import { Card } from '../ui/Card';
import EmptyState from '../ui/EmptyState';
import { SkeletonCard } from '../ui/Skeleton';
import WelcomeChecklist from '../Onboarding/WelcomeChecklist';

function MiniBarChart({ data }: { data: DailyStat[] }) {
  if (!data.length) return <div className="h-28 flex items-center justify-center text-muted-foreground text-xs">No data yet</div>;
  const max = Math.max(...data.map(d => d.queryCount), 1);
  return (
    <div className="h-28 flex items-end gap-px">
      {data.map((d, i) => (
        <div key={i} className="flex-1 flex flex-col items-center gap-0.5 group relative">
          <div
            className="w-full bg-primary rounded-sm opacity-80 hover:opacity-100 transition-opacity"
            style={{ height: `${(d.queryCount / max) * 96}px` }}
          />
          <div className="absolute -top-7 left-1/2 -translate-x-1/2 bg-foreground text-background text-[10px] px-1.5 py-0.5 rounded opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap pointer-events-none z-10">
            {d.date.slice(5)}: {d.queryCount}
          </div>
        </div>
      ))}
    </div>
  );
}

export default function OverviewTab() {
  const { token } = useAuth();
  const [overview, setOverview] = useState<OverviewStats | null>(null);
  const [daily, setDaily] = useState<DailyStat[]>([]);
  const [topQ, setTopQ] = useState<TopQuestion[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!token) return;
    Promise.all([
      fetchOverview(token),
      fetchDailyStats(token, 30),
      fetchTopQuestions(token, 10),
    ]).then(([o, d, q]) => {
      if (o.success && o.data) setOverview(o.data);
      if (d.success && d.data) setDaily(d.data);
      if (q.success && q.data) setTopQ(q.data);
    }).finally(() => setLoading(false));
  }, [token]);

  if (loading) {
    return (
      <div>
        <PageHeader title="Overview" description="Usage and engagement analytics for your tenant." />
        <div className="space-y-6">
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
            {Array.from({ length: 6 }).map((_, i) => <SkeletonCard key={i} />)}
          </div>
          <SkeletonCard className="h-48" />
          <SkeletonCard className="h-64" />
        </div>
      </div>
    );
  }
  if (!overview) {
    return (
      <div>
        <PageHeader title="Overview" description="Usage and engagement analytics for your tenant." />
        <WelcomeChecklist />
        <Card>
          <EmptyState icon={BarChart2} title="No analytics data yet" description="Once users start chatting, usage stats will appear here." />
        </Card>
      </div>
    );
  }

  const qualityTotal = overview.totalPositiveFeedback + overview.totalNegativeFeedback;
  const qualityPct = qualityTotal > 0
    ? Math.round((overview.totalPositiveFeedback / qualityTotal) * 100) : null;

  const cards = [
    { label: 'Queries Today', value: overview.queriesToday, sub: `${overview.queriesThisWeek} this week`, icon: <MessageSquare size={18} className="text-primary" /> },
    { label: 'DAU', value: overview.dauToday, sub: `WAU ${overview.wauThisWeek} · MAU ${overview.mauThisMonth}`, icon: <Users size={18} className="text-accent" /> },
    { label: 'Avg Session Depth', value: overview.avgSessionLength.toFixed(1) + ' msg', sub: 'messages per chat', icon: <TrendingUp size={18} className="text-success" /> },
    { label: 'Answer Quality', value: qualityPct !== null ? qualityPct + '%' : '—', sub: `${overview.totalPositiveFeedback}👍 ${overview.totalNegativeFeedback}👎`, icon: <ThumbsUp size={18} className="text-warning" /> },
    { label: 'Avg Confidence', value: (overview.avgConfidence * 100).toFixed(0) + '%', sub: 'last 30 days', icon: <BarChart2 size={18} className="text-info" /> },
    { label: 'Total Queries', value: overview.totalQueriesAllTime, sub: 'all time', icon: <MessageSquare size={18} className="text-muted-foreground" /> },
  ];

  return (
    <div>
      <PageHeader title="Overview" description="Usage and engagement analytics for your tenant." />
      <WelcomeChecklist />

      <motion.div variants={staggerContainer} initial="hidden" animate="visible" className="space-y-6">
        {/* Metric cards */}
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
          {cards.map(c => (
            <motion.div key={c.label} variants={fadeInUp}>
              <Card className="p-4">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs font-medium text-muted-foreground">{c.label}</span>
                  {c.icon}
                </div>
                <div className="text-2xl font-bold text-foreground">{c.value}</div>
                <div className="text-xs text-muted-foreground mt-0.5">{c.sub}</div>
              </Card>
            </motion.div>
          ))}
        </div>

        {/* Queries/day chart */}
        <motion.div variants={fadeInUp}>
          <Card className="p-5">
            <h3 className="text-sm font-semibold text-foreground mb-3 flex items-center gap-2">
              <BarChart2 size={16} className="text-primary" />
              Queries per Day (last 30 days)
            </h3>
            <MiniBarChart data={daily} />
            <div className="flex justify-between text-[10px] text-muted-foreground mt-1">
              <span>{daily[0]?.date?.slice(5) ?? ''}</span>
              <span>{daily[daily.length - 1]?.date?.slice(5) ?? ''}</span>
            </div>
          </Card>
        </motion.div>

        {/* Top questions */}
        <motion.div variants={fadeInUp}>
          <Card className="p-5">
            <h3 className="text-sm font-semibold text-foreground mb-3">Top 10 Questions (last 30 days)</h3>
            {topQ.length === 0 ? (
              <EmptyState icon={MessageSquare} title="No queries logged yet" description="Top questions will appear here once users start chatting." />
            ) : (
              <div className="space-y-2">
                {topQ.map((q, i) => (
                  <div key={i} className="flex items-center gap-3">
                    <span className="w-5 text-right text-xs text-muted-foreground flex-shrink-0">{i + 1}.</span>
                    <div className="flex-1 min-w-0">
                      <div className="text-sm text-foreground truncate" title={q.questionPreview}>{q.questionPreview}</div>
                      {q.product && (
                        <span className="text-[10px] text-muted-foreground">{q.product} {q.version}</span>
                      )}
                    </div>
                    <span className="text-xs font-semibold text-primary flex-shrink-0">{q.count}×</span>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </motion.div>
      </motion.div>
    </div>
  );
}
