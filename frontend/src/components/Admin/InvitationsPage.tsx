import { useEffect, useState } from 'react';
import { Mail, RefreshCw, AlertCircle, CheckCircle2, Clock, XCircle, Ban } from 'lucide-react';
import { motion } from 'framer-motion';
import {
  listAllInvitations, revokeInvitation,
  type SuperAdminInvitation, type InvitationStatus,
} from '../../services/invitationService';
import { useAuth } from '../../context/AuthContext';
import PageHeader from '../ui/PageHeader';
import { Card } from '../ui/Card';
import Badge from '../ui/Badge';
import Button from '../ui/Button';
import EmptyState from '../ui/EmptyState';
import Spinner from '../ui/Spinner';
import { useToast } from '../ui/Toast';
import { fadeInUp, staggerContainer } from '../../lib/motion';
import { cn } from '../../lib/cn';

type Filter = 'ALL' | InvitationStatus;

const FILTERS: { key: Filter; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'PENDING', label: 'Pending' },
  { key: 'ACCEPTED', label: 'Accepted' },
  { key: 'EXPIRED', label: 'Expired' },
  { key: 'REVOKED', label: 'Revoked' },
];

const STATUS_CONFIG: Record<InvitationStatus, {
  label: string;
  variant: 'primary' | 'success' | 'neutral' | 'danger';
  icon: React.ElementType;
}> = {
  PENDING: { label: 'Pending', variant: 'primary', icon: Clock },
  ACCEPTED: { label: 'Accepted', variant: 'success', icon: CheckCircle2 },
  EXPIRED: { label: 'Expired', variant: 'neutral', icon: XCircle },
  REVOKED: { label: 'Revoked', variant: 'danger', icon: Ban },
};

export default function InvitationsPage() {
  const { token } = useAuth();
  const toast = useToast();
  const [invitations, setInvitations] = useState<SuperAdminInvitation[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [filter, setFilter] = useState<Filter>('ALL');
  const [revokingId, setRevokingId] = useState<string | null>(null);

  useEffect(() => { load(); }, []);

  const load = async () => {
    if (!token) return;
    setLoading(true);
    setLoadError('');
    try {
      const data = await listAllInvitations(token);
      setInvitations(data);
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : 'Failed to load invitations');
    } finally {
      setLoading(false);
    }
  };

  const handleRevoke = async (inv: SuperAdminInvitation) => {
    setRevokingId(inv.id);
    try {
      await revokeInvitation(token!, inv.id);
      setInvitations(prev => prev.map(i => i.id === inv.id ? { ...i, status: 'REVOKED' as InvitationStatus } : i));
      toast.success(`Invitation to ${inv.email} revoked.`);
    } catch (e) {
      const detail = (e as { response?: { data?: { error?: string } } })?.response?.data?.error;
      toast.error(detail || 'Failed to revoke invitation.');
    } finally {
      setRevokingId(null);
    }
  };

  const filtered = filter === 'ALL' ? invitations : invitations.filter(i => i.status === filter);

  const counts: Record<Filter, number> = {
    ALL: invitations.length,
    PENDING: invitations.filter(i => i.status === 'PENDING').length,
    ACCEPTED: invitations.filter(i => i.status === 'ACCEPTED').length,
    EXPIRED: invitations.filter(i => i.status === 'EXPIRED').length,
    REVOKED: invitations.filter(i => i.status === 'REVOKED').length,
  };

  return (
    <motion.div variants={staggerContainer} initial="hidden" animate="visible" className="space-y-6">
      <PageHeader
        title="Invitations"
        description="All admin invitations you have sent, across all tenants."
        actions={
          <Button size="sm" variant="outline" onClick={load} disabled={loading} leftIcon={<RefreshCw size={13} className={cn(loading && 'animate-spin')} />}>
            Refresh
          </Button>
        }
      />

      {/* Status filter tabs */}
      <motion.div variants={fadeInUp}>
        <div className="flex border-b border-border overflow-x-auto">
          {FILTERS.map(({ key, label }) => (
            <button
              key={key}
              onClick={() => setFilter(key)}
              className={cn(
                'px-4 py-2 text-sm font-medium transition-colors whitespace-nowrap flex items-center gap-1.5',
                filter === key
                  ? 'border-b-2 border-primary text-primary'
                  : 'text-muted-foreground hover:text-foreground',
              )}
            >
              {label}
              {counts[key] > 0 && (
                <span className={cn(
                  'rounded-full px-1.5 py-0.5 text-xs font-semibold',
                  filter === key ? 'bg-primary/15 text-primary' : 'bg-muted text-muted-foreground',
                )}>
                  {counts[key]}
                </span>
              )}
            </button>
          ))}
        </div>
      </motion.div>

      {loading ? (
        <div className="flex justify-center py-12"><Spinner size="md" /></div>
      ) : loadError ? (
        <div className="flex items-center justify-center gap-2 py-8 text-danger text-sm">
          <AlertCircle size={16} />{loadError}
        </div>
      ) : filtered.length === 0 ? (
        <Card>
          <EmptyState
            icon={Mail}
            title={filter === 'ALL' ? 'No invitations yet' : `No ${filter.toLowerCase()} invitations`}
            description={filter === 'ALL'
              ? 'Invitations you send from the Tenants page will appear here.'
              : `Switch to "All" to see invitations with other statuses.`}
          />
        </Card>
      ) : (
        <motion.div variants={fadeInUp} className="space-y-2">
          {filtered.map(inv => (
            <InvitationRow
              key={inv.id}
              invitation={inv}
              revoking={revokingId === inv.id}
              onRevoke={() => handleRevoke(inv)}
            />
          ))}
        </motion.div>
      )}
    </motion.div>
  );
}

function InvitationRow({ invitation: inv, revoking, onRevoke }: {
  invitation: SuperAdminInvitation;
  revoking: boolean;
  onRevoke: () => void;
}) {
  const cfg = STATUS_CONFIG[inv.status];
  const StatusIcon = cfg.icon;

  return (
    <Card className="px-4 py-3">
      <div className="flex items-center gap-3 flex-wrap sm:flex-nowrap">
        <StatusIcon size={15} className={cn(
          'flex-shrink-0',
          inv.status === 'ACCEPTED' && 'text-success',
          inv.status === 'PENDING' && 'text-primary',
          inv.status === 'EXPIRED' && 'text-muted-foreground',
          inv.status === 'REVOKED' && 'text-danger',
        )} />

        <div className="flex-1 min-w-0 space-y-0.5">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm font-medium text-foreground">{inv.email}</span>
            <Badge variant="neutral" className="text-xs">{inv.role}</Badge>
            <Badge variant={cfg.variant}>{cfg.label}</Badge>
          </div>
          <div className="text-xs text-muted-foreground flex flex-wrap gap-x-4 gap-y-0.5">
            {inv.tenantName && (
              <span>Tenant: <span className="text-foreground">{inv.tenantName}</span></span>
            )}
            <span>Sent: {formatDate(inv.createdAt)}</span>
            {inv.status === 'PENDING' && (
              <span>Expires: {formatDate(inv.expiresAt)}</span>
            )}
            {inv.status === 'ACCEPTED' && inv.acceptedAt && (
              <span>Accepted: {formatDate(inv.acceptedAt)}</span>
            )}
            {inv.status === 'REVOKED' && inv.revokedAt && (
              <span>Revoked: {formatDate(inv.revokedAt)}</span>
            )}
          </div>
        </div>

        {inv.status === 'PENDING' && (
          <Button
            size="sm"
            variant="ghost"
            onClick={onRevoke}
            disabled={revoking}
            loading={revoking}
            className="text-danger hover:text-danger flex-shrink-0"
          >
            Revoke
          </Button>
        )}
      </div>
    </Card>
  );
}

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
  } catch {
    return iso;
  }
}
