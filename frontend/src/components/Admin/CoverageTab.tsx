import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { useAuth } from '../../context/AuthContext';
import {
  fetchProductCoverage, fetchDocumentCoverage,
  ProductCoverage, DocumentCoverage,
} from '../../services/analyticsService';
import { FileText, AlertTriangle, BarChart2 } from 'lucide-react';
import { Card, CardHeader } from '../ui/Card';
import Badge from '../ui/Badge';
import EmptyState from '../ui/EmptyState';
import { SkeletonCard } from '../ui/Skeleton';
import PageHeader from '../ui/PageHeader';
import { fadeInUp, staggerContainer } from '../../lib/motion';

function ConfidencePill({ value }: { value: number }) {
  const pct = Math.round(value * 100);
  const variant = pct >= 80 ? 'success' : pct >= 60 ? 'warning' : 'danger';
  return <Badge variant={variant}>{pct}%</Badge>;
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

  if (loading) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Coverage"
          description="Product coverage, documentation citation heatmap, and gap signals across your knowledge base."
        />
        <SkeletonCard />
        <SkeletonCard />
        <SkeletonCard />
      </div>
    );
  }

  const maxCitations = Math.max(...docs.map(d => d.citationCount), 1);
  const gaps = products.filter(p => p.lowConfidencePct > 15);

  return (
    <motion.div variants={staggerContainer} initial="hidden" animate="visible" className="space-y-6">
      <PageHeader
        title="Coverage"
        description="Product coverage, documentation citation heatmap, and gap signals across your knowledge base."
      />

      {/* Product Coverage */}
      <motion.div variants={fadeInUp}>
        <Card>
          <CardHeader>
            <h3 className="text-sm font-semibold text-foreground">Product Coverage by Query Volume</h3>
            <p className="text-xs text-muted-foreground mt-0.5">Per product/version: query count, avg confidence, low-confidence rate</p>
          </CardHeader>
          {products.length === 0 ? (
            <EmptyState
              icon={BarChart2}
              title="No queries recorded yet"
              description="Product coverage will appear here once queries start flowing through the system."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-muted text-muted-foreground text-xs uppercase tracking-wide">
                  <tr>
                    <th className="px-5 py-3 text-left">Product</th>
                    <th className="px-5 py-3 text-left">Version</th>
                    <th className="px-5 py-3 text-right">Queries</th>
                    <th className="px-5 py-3 text-center">Avg Confidence</th>
                    <th className="px-5 py-3 text-right">Low Confidence</th>
                    <th className="px-5 py-3 text-right">Low Conf %</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {products.map((p, i) => (
                    <tr key={i} className="hover:bg-surface-hover">
                      <td className="px-5 py-3 font-medium text-foreground">{p.product}</td>
                      <td className="px-5 py-3 text-muted-foreground">{p.version ?? 'all'}</td>
                      <td className="px-5 py-3 text-right text-foreground">{p.queryCount.toLocaleString()}</td>
                      <td className="px-5 py-3 text-center"><ConfidencePill value={p.avgConfidence} /></td>
                      <td className="px-5 py-3 text-right text-foreground">{p.lowConfidenceCount.toLocaleString()}</td>
                      <td className="px-5 py-3 text-right">
                        <span className={`text-xs font-medium ${p.lowConfidencePct > 30 ? 'text-danger' : p.lowConfidencePct > 15 ? 'text-warning' : 'text-success'}`}>
                          {p.lowConfidencePct.toFixed(1)}%
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </motion.div>

      {/* Documentation Heatmap */}
      <motion.div variants={fadeInUp}>
        <Card className="p-5">
          <div className="flex items-center gap-2 mb-1">
            <FileText size={16} className="text-primary" />
            <h3 className="text-sm font-semibold text-foreground">Documentation Citation Heatmap (last 30 days)</h3>
          </div>
          <p className="text-xs text-muted-foreground mb-4">How often each document was cited as a source. Cold documents may be under-used or irrelevant.</p>
          {docs.length === 0 ? (
            <EmptyState
              icon={FileText}
              title="No citations logged yet"
              description="Citation activity will appear here once documents start being referenced in answers."
            />
          ) : (
            <div className="space-y-2">
              {docs.map((d, i) => {
                const pct = (d.citationCount / maxCitations) * 100;
                const heat = pct > 75 ? 'bg-danger' : pct > 50 ? 'bg-warning' : pct > 25 ? 'bg-warning/50' : 'bg-info/50';
                return (
                  <div key={i} className="flex items-center gap-3">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between mb-0.5">
                        <span className="text-xs text-foreground truncate" title={d.documentName}>{d.documentName}</span>
                        <span className="text-xs text-muted-foreground ml-2 flex-shrink-0">{d.citationCount}×</span>
                      </div>
                      <div className="h-2 bg-muted rounded-full overflow-hidden">
                        <div className={`h-full rounded-full ${heat}`} style={{ width: `${pct}%` }} />
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
          <div className="flex items-center gap-3 mt-4 text-xs text-muted-foreground">
            <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-sm bg-info/50 inline-block" /> Low</span>
            <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-sm bg-warning/50 inline-block" /> Medium</span>
            <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-sm bg-warning inline-block" /> High</span>
            <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-sm bg-danger inline-block" /> Very High</span>
          </div>
        </Card>
      </motion.div>

      {/* Gap detector */}
      <motion.div variants={fadeInUp}>
        <Card className="p-5">
          <div className="flex items-center gap-2 mb-1">
            <AlertTriangle size={16} className="text-warning" />
            <h3 className="text-sm font-semibold text-foreground">Documentation Gap Signals</h3>
          </div>
          <p className="text-xs text-muted-foreground mb-4">Products with &gt;15% low-confidence queries may have documentation gaps.</p>
          {gaps.length === 0 ? (
            <div className="text-sm text-success bg-success/10 rounded-lg px-4 py-3">
              No significant gaps detected. All products have &lt;15% low-confidence query rate.
            </div>
          ) : (
            <div className="space-y-2">
              {gaps.map((p, i) => (
                <div key={i} className="flex items-center justify-between bg-warning/10 border border-warning/20 rounded-lg px-4 py-2.5">
                  <div>
                    <span className="text-sm font-medium text-warning">{p.product}</span>
                    {p.version && <span className="text-xs text-warning ml-1">v{p.version}</span>}
                    <p className="text-xs text-warning mt-0.5">{p.lowConfidenceCount} of {p.queryCount} queries returned low-confidence answers</p>
                  </div>
                  <span className="text-lg font-bold text-warning">{p.lowConfidencePct.toFixed(0)}%</span>
                </div>
              ))}
            </div>
          )}
        </Card>
      </motion.div>
    </motion.div>
  );
}
