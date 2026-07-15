import { useMemo, useState } from 'react';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, ResponsiveContainer, LabelList,
} from 'recharts';
import { useAuth } from '../../context/AuthContext';
import { fetchCostSummary } from '../../services/analyticsService';
import { downloadCsv } from '../../lib/csvExport';
import { useChartTheme } from '../../lib/chartTheme';
import { DollarSign, User, Package, Download } from 'lucide-react';
import { Card, CardHeader } from '../ui/Card';
import Button from '../ui/Button';
import EmptyState from '../ui/EmptyState';
import { SkeletonCard } from '../ui/Skeleton';
import DateRangePicker from '../ui/DateRangePicker';
import ChartTooltip from '../ui/charts/ChartTooltip';
import PageHeader from '../ui/PageHeader';
import { fadeInUp, staggerContainer } from '../../lib/motion';

function fmt(n: number) {
  return n < 0.01 ? `$${(n * 1000).toFixed(2)}m` : `$${n.toFixed(4)}`;
}

export default function CostTrackingTab() {
  const { token } = useAuth();
  const chart = useChartTheme();
  const [days, setDays] = useState(30);

  const costQuery = useQuery({
    queryKey: ['analytics-cost', days],
    queryFn: () => fetchCostSummary(token!, days),
    enabled: !!token,
    placeholderData: keepPreviousData,
  });

  const cost = costQuery.data?.success ? costQuery.data.data ?? null : null;
  const loading = costQuery.isLoading;

  const dailyChartData = useMemo(
    () => (cost?.dailyCost ?? []).map(d => ({ ...d, dateLabel: d.date.slice(5) })),
    [cost],
  );

  const handleExportDaily = () => {
    if (!cost) return;
    downloadCsv(`daily-cost-last-${days}d.csv`, cost.dailyCost, ['date', 'queryCount', 'estimatedCost']);
  };
  const handleExportByUser = () => {
    if (!cost) return;
    downloadCsv(`cost-by-user-last-${days}d.csv`, cost.costByUser, ['username', 'queryCount', 'totalCost']);
  };
  const handleExportByProduct = () => {
    if (!cost) return;
    downloadCsv(`cost-by-product-last-${days}d.csv`, cost.costByProduct, ['product', 'version', 'queryCount', 'totalCost']);
  };

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
    <div>
      <PageHeader
        title="Cost"
        description="Estimated LLM spend for your tenant."
        actions={<DateRangePicker days={days} onChange={d => setDays(d ?? 30)} />}
      />
      <motion.div variants={staggerContainer} initial="hidden" animate="visible" className="space-y-6">
        {/* Summary cards */}
        <motion.div variants={fadeInUp} className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          {[
            { label: `Cost, last ${days}d`, value: `$${cost.totalCostThisMonth.toFixed(4)}`, sub: 'estimated LLM cost', icon: <DollarSign size={18} className="text-success" /> },
            { label: 'Avg cost / query', value: `$${cost.avgCostPerQuery.toFixed(5)}`, sub: `last ${days} days`, icon: <DollarSign size={18} className="text-primary" /> },
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
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-sm font-semibold text-foreground">Daily Cost Trend (last {days} days)</h3>
              <Button variant="ghost" size="sm" onClick={handleExportDaily} leftIcon={<Download size={13} />}>Export CSV</Button>
            </div>
            {dailyChartData.length === 0 ? (
              <EmptyState icon={DollarSign} title="No data yet" className="py-8" />
            ) : (
              <div className="h-56" style={{ opacity: costQuery.isFetching ? 0.6 : 1, transition: 'opacity 150ms' }}>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={dailyChartData} margin={{ top: 4, right: 8, left: -8, bottom: 0 }}>
                    <CartesianGrid stroke={chart.grid} vertical={false} />
                    <XAxis dataKey="dateLabel" stroke={chart.axis} tick={{ fill: chart.mutedText, fontSize: 11 }} tickLine={false} />
                    <YAxis
                      stroke={chart.axis}
                      tick={{ fill: chart.mutedText, fontSize: 11 }}
                      tickLine={false}
                      width={56}
                      tickFormatter={(v: number) => `$${v.toFixed(3)}`}
                    />
                    <RechartsTooltip
                      cursor={{ fill: chart.grid, opacity: 0.4 }}
                      content={({ active, label, payload }) => (
                        <ChartTooltip
                          active={active}
                          label={label}
                          items={(payload ?? []).map(p => ({
                            name: 'Cost', value: `$${Number(p.value).toFixed(4)}`, color: chart.sequential,
                          }))}
                        />
                      )}
                    />
                    <Bar dataKey="estimatedCost" name="Cost" fill={chart.sequential} radius={[4, 4, 0, 0]} maxBarSize={28} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
            <p className="text-xs text-muted-foreground mt-2">Estimated based on token usage × model pricing. Defaults: gpt-4o-mini ($0.15/1M input, $0.60/1M output).</p>
          </Card>
        </motion.div>

        {/* Cost by user / product */}
        <motion.div variants={fadeInUp} className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <Card>
            <CardHeader className="flex items-center justify-between gap-2">
              <span className="flex items-center gap-2">
                <User size={16} className="text-primary" />
                <h3 className="text-sm font-semibold text-foreground">Cost by User (top 10)</h3>
              </span>
              <Button variant="ghost" size="sm" onClick={handleExportByUser} leftIcon={<Download size={13} />}>CSV</Button>
            </CardHeader>
            {cost.costByUser.length === 0 ? (
              <EmptyState icon={User} title="No data" className="py-8" />
            ) : (
              <div className="p-4 h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={cost.costByUser} layout="vertical" margin={{ top: 4, right: 16, left: 4, bottom: 0 }}>
                    <CartesianGrid stroke={chart.grid} horizontal={false} />
                    <XAxis type="number" stroke={chart.axis} tick={{ fill: chart.mutedText, fontSize: 11 }} tickLine={false} tickFormatter={(v: number) => `$${v.toFixed(2)}`} />
                    <YAxis type="category" dataKey="username" stroke={chart.axis} tick={{ fill: chart.mutedText, fontSize: 11 }} tickLine={false} width={80} />
                    <RechartsTooltip
                      cursor={{ fill: chart.grid, opacity: 0.4 }}
                      content={({ active, label, payload }) => (
                        <ChartTooltip
                          active={active}
                          label={label}
                          items={(payload ?? []).map(p => ({ name: 'Cost', value: fmt(Number(p.value)), color: chart.sequential }))}
                        />
                      )}
                    />
                    <Bar dataKey="totalCost" name="Cost" fill={chart.sequential} radius={[0, 4, 4, 0]} maxBarSize={18}>
                      <LabelList dataKey="totalCost" position="right" formatter={(v) => fmt(Number(v))} fill={chart.text} fontSize={11} />
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </Card>

          <Card>
            <CardHeader className="flex items-center justify-between gap-2">
              <span className="flex items-center gap-2">
                <Package size={16} className="text-accent" />
                <h3 className="text-sm font-semibold text-foreground">Cost by Product</h3>
              </span>
              <Button variant="ghost" size="sm" onClick={handleExportByProduct} leftIcon={<Download size={13} />}>CSV</Button>
            </CardHeader>
            {cost.costByProduct.length === 0 ? (
              <EmptyState icon={Package} title="No data" className="py-8" />
            ) : (
              <div className="p-4 h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={cost.costByProduct} layout="vertical" margin={{ top: 4, right: 16, left: 4, bottom: 0 }}>
                    <CartesianGrid stroke={chart.grid} horizontal={false} />
                    <XAxis type="number" stroke={chart.axis} tick={{ fill: chart.mutedText, fontSize: 11 }} tickLine={false} tickFormatter={(v: number) => `$${v.toFixed(2)}`} />
                    <YAxis type="category" dataKey="product" stroke={chart.axis} tick={{ fill: chart.mutedText, fontSize: 11 }} tickLine={false} width={80} />
                    <RechartsTooltip
                      cursor={{ fill: chart.grid, opacity: 0.4 }}
                      content={({ active, label, payload }) => (
                        <ChartTooltip
                          active={active}
                          label={label}
                          items={(payload ?? []).map(p => ({ name: 'Cost', value: fmt(Number(p.value)), color: chart.sequential }))}
                        />
                      )}
                    />
                    <Bar dataKey="totalCost" name="Cost" fill={chart.sequential} radius={[0, 4, 4, 0]} maxBarSize={18}>
                      <LabelList dataKey="totalCost" position="right" formatter={(v) => fmt(Number(v))} fill={chart.text} fontSize={11} />
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </Card>
        </motion.div>
      </motion.div>
    </div>
  );
}
