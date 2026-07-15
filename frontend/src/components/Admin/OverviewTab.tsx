import { useMemo, useState } from 'react';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, ResponsiveContainer, LabelList,
} from 'recharts';
import { useAuth } from '../../context/AuthContext';
import {
  fetchOverview, fetchDailyStats, fetchTopQuestions, fetchProductCoverage,
} from '../../services/analyticsService';
import { downloadCsv } from '../../lib/csvExport';
import { useChartTheme } from '../../lib/chartTheme';
import { TrendingUp, Users, MessageSquare, ThumbsUp, BarChart2, Download, Map } from 'lucide-react';
import { fadeInUp, staggerContainer } from '../../lib/motion';
import PageHeader from '../ui/PageHeader';
import { Card } from '../ui/Card';
import Button from '../ui/Button';
import EmptyState from '../ui/EmptyState';
import { SkeletonCard } from '../ui/Skeleton';
import DateRangePicker from '../ui/DateRangePicker';
import ChartTooltip from '../ui/charts/ChartTooltip';
import WelcomeChecklist from '../Onboarding/WelcomeChecklist';

export default function OverviewTab() {
  const { token } = useAuth();
  const chart = useChartTheme();
  const [days, setDays] = useState(30);

  const overviewQuery = useQuery({
    queryKey: ['analytics-overview'],
    queryFn: () => fetchOverview(token!),
    enabled: !!token,
  });
  const dailyQuery = useQuery({
    queryKey: ['analytics-daily', days],
    queryFn: () => fetchDailyStats(token!, days),
    enabled: !!token,
    placeholderData: keepPreviousData,
  });
  const topQQuery = useQuery({
    queryKey: ['analytics-top-questions', days],
    queryFn: () => fetchTopQuestions(token!, 10, days),
    enabled: !!token,
    placeholderData: keepPreviousData,
  });
  const coverageQuery = useQuery({
    queryKey: ['analytics-product-coverage'],
    queryFn: () => fetchProductCoverage(token!),
    enabled: !!token,
  });

  const overview = overviewQuery.data?.success ? overviewQuery.data.data ?? null : null;
  const daily = dailyQuery.data?.success ? dailyQuery.data.data ?? [] : [];
  const topQ = topQQuery.data?.success ? topQQuery.data.data ?? [] : [];
  const coverage = coverageQuery.data?.success ? coverageQuery.data.data ?? [] : [];
  const loading = overviewQuery.isLoading;

  // Worst-covered products first — the "coverage gap" a tenant admin would act on.
  const worstCoverage = useMemo(() => (
    [...coverage].sort((a, b) => b.lowConfidencePct - a.lowConfidencePct).slice(0, 8)
  ), [coverage]);

  const chartData = useMemo(() => daily.map(d => ({ ...d, dateLabel: d.date.slice(5) })), [daily]);

  const handleExportDaily = () => {
    downloadCsv(`query-volume-last-${days}d.csv`, daily, ['date', 'queryCount', 'avgConfidence', 'estimatedCost']);
  };

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
      <PageHeader
        title="Overview"
        description="Usage and engagement analytics for your tenant."
        actions={<DateRangePicker days={days} onChange={d => setDays(d ?? 30)} />}
      />
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
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-sm font-semibold text-foreground flex items-center gap-2">
                <BarChart2 size={16} className="text-primary" />
                Queries per Day (last {days} days)
              </h3>
              <Button variant="ghost" size="sm" onClick={handleExportDaily} leftIcon={<Download size={13} />}>
                Export CSV
              </Button>
            </div>
            {chartData.length === 0 ? (
              <EmptyState icon={BarChart2} title="No data yet" className="py-8" />
            ) : (
              <div className="h-56" style={{ opacity: dailyQuery.isFetching ? 0.6 : 1, transition: 'opacity 150ms' }}>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={chartData} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
                    <CartesianGrid stroke={chart.grid} vertical={false} />
                    <XAxis dataKey="dateLabel" stroke={chart.axis} tick={{ fill: chart.mutedText, fontSize: 11 }} tickLine={false} />
                    <YAxis stroke={chart.axis} tick={{ fill: chart.mutedText, fontSize: 11 }} tickLine={false} allowDecimals={false} width={36} />
                    <RechartsTooltip
                      cursor={{ fill: chart.grid, opacity: 0.4 }}
                      content={({ active, label, payload }) => (
                        <ChartTooltip
                          active={active}
                          label={label}
                          items={(payload ?? []).map(p => ({
                            name: 'Queries', value: String(p.value), color: chart.sequential,
                          }))}
                        />
                      )}
                    />
                    <Bar dataKey="queryCount" name="Queries" fill={chart.sequential} radius={[4, 4, 0, 0]} maxBarSize={28} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </Card>
        </motion.div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Top questions */}
          <motion.div variants={fadeInUp}>
            <Card className="p-5 h-full">
              <h3 className="text-sm font-semibold text-foreground mb-3">Top 10 Questions (last {days} days)</h3>
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

          {/* Coverage gaps */}
          <motion.div variants={fadeInUp}>
            <Card className="p-5 h-full">
              <h3 className="text-sm font-semibold text-foreground mb-3 flex items-center gap-2">
                <Map size={16} className="text-primary" />
                Coverage Gaps — % Low-Confidence Answers
              </h3>
              {worstCoverage.length === 0 ? (
                <EmptyState icon={Map} title="No coverage data yet" className="py-8" />
              ) : (
                <div className="h-56">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={worstCoverage} layout="vertical" margin={{ top: 4, right: 16, left: 4, bottom: 0 }}>
                      <CartesianGrid stroke={chart.grid} horizontal={false} />
                      <XAxis type="number" stroke={chart.axis} tick={{ fill: chart.mutedText, fontSize: 11 }} tickLine={false} unit="%" />
                      <YAxis
                        type="category"
                        dataKey="product"
                        stroke={chart.axis}
                        tick={{ fill: chart.mutedText, fontSize: 11 }}
                        tickLine={false}
                        width={90}
                      />
                      <RechartsTooltip
                        cursor={{ fill: chart.grid, opacity: 0.4 }}
                        content={({ active, label, payload }) => (
                          <ChartTooltip
                            active={active}
                            label={label}
                            items={(payload ?? []).map(p => ({
                              name: 'Low-confidence', value: `${Number(p.value).toFixed(0)}%`, color: chart.sequential,
                            }))}
                          />
                        )}
                      />
                      <Bar dataKey="lowConfidencePct" name="Low-confidence %" fill={chart.sequential} radius={[0, 4, 4, 0]} maxBarSize={18}>
                        <LabelList dataKey="lowConfidencePct" position="right" formatter={(v) => `${Number(v).toFixed(0)}%`} fill={chart.text} fontSize={11} />
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </Card>
          </motion.div>
        </div>
      </motion.div>
    </div>
  );
}
