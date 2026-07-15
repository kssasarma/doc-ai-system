import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { AlertTriangle, Download, ChevronDown, ChevronUp } from 'lucide-react';
import {
  listGapReports,
  generateGapReport,
  exportGapReport,
  type GapReport,
} from '../../services/gapReportService';
import { useAuth } from '../../context/AuthContext';
import { fadeInUp, staggerContainer } from '../../lib/motion';
import PageHeader from '../ui/PageHeader';
import { Card } from '../ui/Card';
import Button from '../ui/Button';
import IconButton from '../ui/IconButton';
import Badge from '../ui/Badge';
import Input from '../ui/Input';
import EmptyState from '../ui/EmptyState';
import { SkeletonCard } from '../ui/Skeleton';

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
    <motion.div variants={staggerContainer} initial="hidden" animate="visible">
      <PageHeader
        title="Documentation Gap Reports"
        description="AI-generated reports identifying topics with insufficient documentation coverage based on query failure analysis."
      />

      <div className="space-y-6">
        {/* Generate */}
        <motion.div variants={fadeInUp}>
          <Card className="p-4 bg-warning/10 border-warning/20">
            <div className="flex items-center gap-2 mb-2 text-sm font-medium text-warning">
              <AlertTriangle size={14} /> Generate gap report
            </div>
            <div className="flex gap-2">
              <Input
                className="flex-1"
                aria-label="Product (optional — all if blank)"
                placeholder="Product (optional — all if blank)"
                value={product}
                onChange={e => setProduct(e.target.value)}
              />
              <Input
                className="w-32"
                aria-label="Version"
                placeholder="Version"
                value={version}
                onChange={e => setVersion(e.target.value)}
              />
              <Button
                variant="primary"
                onClick={handleGenerate}
                disabled={generating}
                loading={generating}
                leftIcon={!generating ? <AlertTriangle size={14} /> : undefined}
              >
                Generate
              </Button>
            </div>
            {msg && <p className="text-xs mt-2 text-warning">{msg}</p>}
          </Card>
        </motion.div>

        {loading && (
          <motion.div variants={fadeInUp} className="space-y-4">
            {[0, 1, 2].map(i => <SkeletonCard key={i} />)}
          </motion.div>
        )}

        {!loading && reports.length === 0 && (
          <motion.div variants={fadeInUp}>
            <EmptyState
              icon={AlertTriangle}
              title="No gap reports yet"
              description="Generate your first report above or wait for the monthly scheduled job."
            />
          </motion.div>
        )}

        <motion.div variants={fadeInUp} className="space-y-4">
          {reports.map(report => (
            <GapReportCard
              key={report.id}
              report={report}
              expanded={expandedId === report.id}
              onToggle={() => setExpandedId(prev => prev === report.id ? null : report.id)}
              onExport={() => handleExport(report.id)}
            />
          ))}
        </motion.div>
      </div>
    </motion.div>
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
    <Card className="overflow-hidden">
      <div className="flex items-center gap-3 p-4">
        <button onClick={onToggle} className="flex-1 text-left">
          <div className="flex items-center gap-2 mb-0.5">
            {report.product && (
              <Badge variant="primary">
                {report.product}{report.version ? ` ${report.version}` : ''}
              </Badge>
            )}
            <span className="text-xs text-muted-foreground">
              {report.reportPeriodStart} → {report.reportPeriodEnd}
            </span>
          </div>
          <div className="flex items-center gap-3 text-sm text-foreground">
            <span className="font-medium">{topics.length} gap{topics.length !== 1 ? 's' : ''} identified</span>
            <span className="text-muted-foreground">·</span>
            <span className="text-muted-foreground">{report.totalLowConfidenceQueries} low-confidence queries</span>
          </div>
        </button>
        <div className="flex items-center gap-2 flex-shrink-0">
          <Button
            variant="outline"
            size="sm"
            onClick={onExport}
            leftIcon={<Download size={12} />}
          >
            Export
          </Button>
          <IconButton label={expanded ? 'Collapse gap details' : 'Expand gap details'} variant="ghost" size="sm" onClick={onToggle}>
            {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
          </IconButton>
        </div>
      </div>

      {expanded && topics.length > 0 && (
        <div className="border-t border-border divide-y divide-border">
          {topics.map((t, i) => (
            <div key={i} className="px-4 py-3">
              <div className="flex items-start justify-between gap-3 mb-1">
                <p className="text-sm font-medium text-foreground">{t.topic}</p>
                <div className="flex gap-2 flex-shrink-0 text-xs text-muted-foreground">
                  <span>{t.queryCount} queries</span>
                  <span>·</span>
                  <span>{t.uniqueUsers} user{t.uniqueUsers !== 1 ? 's' : ''}</span>
                </div>
              </div>
              {t.exampleQuestions?.length > 0 && (
                <div className="mb-2">
                  <p className="text-xs text-muted-foreground mb-1">Example questions:</p>
                  <ul className="text-xs text-muted-foreground space-y-0.5">
                    {t.exampleQuestions.slice(0, 3).map((q, j) => (
                      <li key={j} className="truncate">• {q}</li>
                    ))}
                  </ul>
                </div>
              )}
              {t.suggestedDocStub && (
                <div className="bg-warning/10 border border-warning/20 rounded-lg p-2.5">
                  <p className="text-xs font-medium text-warning mb-1">Suggested documentation stub:</p>
                  <p className="text-xs text-warning leading-relaxed line-clamp-4">{t.suggestedDocStub}</p>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}
