import React, { useEffect, useState, useCallback } from 'react';
import { motion } from 'framer-motion';
import { useAuth } from '../../context/AuthContext';
import { fetchAuditLog, AuditLogEntry } from '../../services/auditLogService';
import { Shield, ChevronLeft, ChevronRight } from 'lucide-react';
import { Card } from '../ui/Card';
import Badge, { BadgeProps } from '../ui/Badge';
import EmptyState from '../ui/EmptyState';
import { SkeletonRow } from '../ui/Skeleton';
import PageHeader from '../ui/PageHeader';
import Select from '../ui/Select';
import IconButton from '../ui/IconButton';
import { fadeInUp, staggerContainer } from '../../lib/motion';

const ACTION_VARIANTS: Record<string, BadgeProps['variant']> = {
  PRODUCT_ACCESS_GRANT: 'success',
  PRODUCT_ACCESS_REVOKE: 'danger',
  ESCALATION_ANSWER: 'warning',
  DOCUMENT_UPLOAD: 'primary',
  DOCUMENT_DELETE: 'danger',
  USER_ROLE_CHANGE: 'neutral',
  SHARE_LINK_CREATE: 'info',
  SHARE_LINK_REVOKE: 'neutral',
  COLLECTION_CREATE: 'neutral',
};

// Actions whose semantic color (purple/indigo) has no dedicated Badge variant —
// applied as a className override on top of the "neutral" base variant.
const ACTION_ACCENT_CLASS: Record<string, string> = {
  USER_ROLE_CHANGE: 'bg-accent/10 text-accent',
  COLLECTION_CREATE: 'bg-accent/10 text-accent',
};

function ActionBadge({ action }: { action: string }) {
  return (
    <Badge variant={ACTION_VARIANTS[action] ?? 'neutral'} className={ACTION_ACCENT_CLASS[action]}>
      {action}
    </Badge>
  );
}

export default function AuditLogTab() {
  const { token } = useAuth();
  const [entries, setEntries] = useState<AuditLogEntry[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [actionFilter, setActionFilter] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(async (pg: number, action: string) => {
    if (!token) return;
    setLoading(true);
    const res = await fetchAuditLog(token, pg, 50, action || undefined);
    if (res.success && res.data) {
      setEntries(res.data.content);
      setTotalPages(res.data.totalPages);
      setTotalElements(res.data.totalElements);
    }
    setLoading(false);
  }, [token]);

  useEffect(() => { load(page, actionFilter); }, [load, page, actionFilter]);

  return (
    <motion.div variants={staggerContainer} initial="hidden" animate="visible" className="space-y-4">
      <PageHeader
        title="Audit Log"
        description="A chronological record of administrative actions taken across the tenant."
      />

      {/* Filters */}
      <motion.div variants={fadeInUp}>
        <Card className="p-4 flex items-center gap-3">
          <Shield size={16} className="text-muted-foreground" />
          <span className="text-sm font-medium text-muted-foreground">Filter by action:</span>
          <div className="w-52">
            <Select
              aria-label="Filter by action"
              value={actionFilter}
              onChange={e => { setActionFilter(e.target.value); setPage(0); }}
            >
              <option value="">All actions</option>
              <option value="PRODUCT_ACCESS_GRANT">Access Grant</option>
              <option value="PRODUCT_ACCESS_REVOKE">Access Revoke</option>
              <option value="ESCALATION_ANSWER">Escalation Answer</option>
              <option value="DOCUMENT_UPLOAD">Document Upload</option>
              <option value="DOCUMENT_DELETE">Document Delete</option>
              <option value="USER_ROLE_CHANGE">Role Change</option>
            </Select>
          </div>
          <span className="text-xs text-muted-foreground ml-auto">{totalElements.toLocaleString()} total entries</span>
        </Card>
      </motion.div>

      {/* Log table */}
      <motion.div variants={fadeInUp}>
        <Card>
          {loading ? (
            <div className="divide-y divide-border">
              {Array.from({ length: 6 }).map((_, i) => <SkeletonRow key={i} columns={5} />)}
            </div>
          ) : entries.length === 0 ? (
            <EmptyState
              icon={Shield}
              title="No audit log entries yet"
              description="Administrative actions taken across the tenant will be recorded here."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-muted text-muted-foreground text-xs uppercase tracking-wide">
                  <tr>
                    <th className="px-4 py-3 text-left">Timestamp</th>
                    <th className="px-4 py-3 text-left">Actor</th>
                    <th className="px-4 py-3 text-left">Action</th>
                    <th className="px-4 py-3 text-left">Target</th>
                    <th className="px-4 py-3 text-left">Details</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {entries.map(e => (
                    <tr key={e.id} className="hover:bg-surface-hover">
                      <td className="px-4 py-3 text-xs text-muted-foreground whitespace-nowrap">
                        {new Date(e.createdAt).toLocaleString()}
                      </td>
                      <td className="px-4 py-3 text-foreground font-medium">
                        {e.actorUsername ?? 'system'}
                      </td>
                      <td className="px-4 py-3">
                        <ActionBadge action={e.action} />
                      </td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">
                        {e.targetType ?? '—'}
                        {e.targetId && <div className="font-mono text-[10px] text-muted-foreground truncate max-w-24" title={e.targetId}>{e.targetId.slice(0, 8)}…</div>}
                      </td>
                      <td className="px-4 py-3 text-xs text-muted-foreground max-w-xs truncate" title={e.metadata ?? ''}>
                        {e.metadata ?? '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="px-4 py-3 border-t border-border flex items-center justify-between">
              <span className="text-xs text-muted-foreground">Page {page + 1} of {totalPages}</span>
              <div className="flex items-center gap-2">
                <IconButton
                  label="Previous page"
                  variant="ghost"
                  size="sm"
                  onClick={() => setPage(p => Math.max(0, p - 1))}
                  disabled={page === 0}
                >
                  <ChevronLeft size={16} />
                </IconButton>
                <IconButton
                  label="Next page"
                  variant="ghost"
                  size="sm"
                  onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                >
                  <ChevronRight size={16} />
                </IconButton>
              </div>
            </div>
          )}
        </Card>
      </motion.div>
    </motion.div>
  );
}
