import { useState, useEffect, useCallback } from 'react';
import { motion } from 'framer-motion';
import { useAuth } from '../../context/AuthContext';
import { PiiFlag } from '../../types';
import { fetchPiiFlags, reviewPiiFlag, releaseQuarantinedDocument, rejectQuarantinedDocument } from '../../services/piiFlagService';
import { ShieldAlert, Check, Lock, Trash2 } from 'lucide-react';
import { fadeInUp, staggerContainer } from '../../lib/motion';
import PageHeader from '../ui/PageHeader';
import { Card } from '../ui/Card';
import Button from '../ui/Button';
import Badge, { type BadgeProps } from '../ui/Badge';
import EmptyState from '../ui/EmptyState';
import { SkeletonRow } from '../ui/Skeleton';
import { useToast } from '../ui/Toast';
import { useConfirm } from '../ui/ConfirmDialog';

const RISK_BADGE: Record<PiiFlag['riskLevel'], NonNullable<BadgeProps['variant']>> = {
  CRITICAL: 'danger',
  HIGH: 'danger',
  MEDIUM: 'warning',
  LOW: 'neutral',
};

export default function PiiFlagsTab() {
  const { token } = useAuth();
  const toast = useToast();
  const confirm = useConfirm();
  const [flags, setFlags] = useState<PiiFlag[]>([]);
  const [loading, setLoading] = useState(true);
  const [actioningId, setActioningId] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    const res = await fetchPiiFlags(token, false);
    if (res.success && res.data) setFlags(res.data);
    setLoading(false);
  }, [token]);

  useEffect(() => { load(); }, [load]);

  const handleReview = async (id: string, actionTaken: 'ACKNOWLEDGED' | 'REDACTED' | 'DISMISSED') => {
    if (!token) return;
    setActioningId(id);
    const res = await reviewPiiFlag(id, actionTaken, token);
    if (res.success) {
      setFlags(prev => prev.filter(f => f.id !== id));
      toast.success('Flag reviewed.');
    } else {
      toast.error(res.error ?? 'Failed to review flag.');
    }
    setActioningId(null);
  };

  // CRITICAL flags hold the whole document in quarantine (see IngestionService#completeIngestion
  // on the backend) — release/reject act on the document, not the individual flag, since a
  // document can carry several flags at once and can't be "half quarantined".
  const handleRelease = async (documentId: string, flagId: string) => {
    if (!token) return;
    setActioningId(flagId);
    const res = await releaseQuarantinedDocument(documentId, token);
    if (res.success) {
      setFlags(prev => prev.filter(f => f.documentId !== documentId));
      toast.success('Document released — it is now searchable again.');
    } else {
      toast.error(res.error ?? 'Failed to release document.');
    }
    setActioningId(null);
  };

  const handleReject = async (documentId: string, flagId: string) => {
    if (!token) return;
    const confirmed = await confirm({
      title: 'Reject document',
      message: 'Reject and permanently delete this document? This cannot be undone.',
      confirmLabel: 'Reject & delete',
      danger: true,
    });
    if (!confirmed) return;
    setActioningId(flagId);
    const res = await rejectQuarantinedDocument(documentId, token);
    if (res.success) {
      setFlags(prev => prev.filter(f => f.documentId !== documentId));
      toast.success('Document rejected and deleted.');
    } else {
      toast.error(res.error ?? 'Failed to reject document.');
    }
    setActioningId(null);
  };

  return (
    <motion.div variants={staggerContainer} initial="hidden" animate="visible">
      <PageHeader
        title="PII Review"
        description="Personally identifiable information detected in ingested documents, pending admin review."
        actions={<Badge variant="warning">{flags.length} pending</Badge>}
      />
      <motion.div variants={fadeInUp}>
        <Card>
          {loading && (
            <div className="py-2">
              <SkeletonRow columns={4} />
              <SkeletonRow columns={4} />
              <SkeletonRow columns={4} />
            </div>
          )}
          {!loading && flags.length === 0 && (
            <EmptyState
              icon={ShieldAlert}
              title="No PII flags pending review"
              description="Documents are scanned for PII on ingestion; anything found appears here for review."
            />
          )}
          {!loading && flags.length > 0 && (
            <div className="divide-y divide-border">
              {flags.map(flag => (
                <div key={flag.id} className="px-6 py-4 flex items-start justify-between gap-4">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-medium text-foreground">{flag.piiType}</span>
                      <Badge variant={RISK_BADGE[flag.riskLevel]}>{flag.riskLevel}</Badge>
                      <span className="text-xs text-muted-foreground">
                        {flag.occurrenceCount} occurrence{flag.occurrenceCount !== 1 ? 's' : ''}
                      </span>
                    </div>
                    {flag.sampleExcerpt && (
                      <p className="text-xs text-muted-foreground font-mono mt-1">{flag.sampleExcerpt}</p>
                    )}
                    <p className="text-xs text-muted-foreground mt-1">
                      Document {flag.documentId.slice(0, 8)} · {new Date(flag.createdAt).toLocaleDateString()}
                    </p>
                  </div>
                  <div className="flex items-center gap-2 flex-shrink-0">
                    {flag.riskLevel === 'CRITICAL' ? (
                      <>
                        <Button
                          variant="outline"
                          size="sm"
                          loading={actioningId === flag.id}
                          leftIcon={<Lock className="w-3 h-3" />}
                          onClick={() => handleRelease(flag.documentId, flag.id)}
                        >
                          Release
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          loading={actioningId === flag.id}
                          leftIcon={<Trash2 className="w-3 h-3" />}
                          onClick={() => handleReject(flag.documentId, flag.id)}
                        >
                          Reject &amp; delete
                        </Button>
                      </>
                    ) : (
                      <>
                        <Button
                          variant="outline"
                          size="sm"
                          loading={actioningId === flag.id}
                          leftIcon={<Check className="w-3 h-3" />}
                          onClick={() => handleReview(flag.id, 'ACKNOWLEDGED')}
                        >
                          Acknowledge
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          loading={actioningId === flag.id}
                          onClick={() => handleReview(flag.id, 'DISMISSED')}
                        >
                          Dismiss
                        </Button>
                      </>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>
      </motion.div>
    </motion.div>
  );
}
