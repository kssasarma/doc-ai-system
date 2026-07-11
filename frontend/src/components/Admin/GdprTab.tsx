import React, { useEffect, useState } from 'react';
import { Shield, Trash2, Download, RefreshCw } from 'lucide-react';
import axios from 'axios';
import { motion } from 'framer-motion';
import { useAuth } from '../../context/AuthContext';
import PageHeader from '../ui/PageHeader';
import { Card } from '../ui/Card';
import Badge from '../ui/Badge';
import EmptyState from '../ui/EmptyState';
import IconButton from '../ui/IconButton';
import Button from '../ui/Button';
import { SkeletonRow } from '../ui/Skeleton';
import { useToast } from '../ui/Toast';
import { fadeInUp, staggerContainer } from '../../lib/motion';
import { cn } from '../../lib/cn';

const BOT_URL = import.meta.env.VITE_BOT_API_URL ?? 'http://localhost:8082';

interface DeletionRequest {
  id: string;
  userId: string;
  requestedAt: string;
  status: string;
}

export default function GdprTab() {
  const { token } = useAuth();
  const toast = useToast();
  const [requests, setRequests] = useState<DeletionRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [processingId, setProcessingId] = useState<string | null>(null);

  useEffect(() => { load(); }, []);

  const load = async () => {
    if (!token) return;
    setLoading(true);
    try {
      const { data } = await axios.get<DeletionRequest[]>(
        `${BOT_URL}/api/user/gdpr/admin/deletion-requests`,
        { headers: { Authorization: `Bearer ${token}` } },
      );
      setRequests(data);
    } catch { /* ignore */ }
    setLoading(false);
  };

  const handleProcess = async (id: string, userId: string) => {
    if (!token) return;
    setProcessingId(id);
    try {
      await axios.delete(`${BOT_URL}/api/admin/users/${userId}`,
        { headers: { Authorization: `Bearer ${token}` } });
      setRequests(prev => prev.filter(r => r.id !== id));
      toast.success('User data deleted successfully.');
    } catch {
      toast.error('Failed to process deletion request.');
    }
    setProcessingId(null);
  };

  return (
    <motion.div variants={staggerContainer} initial="hidden" animate="visible" className="space-y-6">
      <PageHeader
        title="GDPR & Compliance"
        description="Manage data subject requests. Pending deletion requests must be processed within 30 days (GDPR Article 17)."
      />

      <motion.div variants={fadeInUp} className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <ComplianceCard icon={<Shield size={18} className="text-success" />} title="Data Portability"
          description="Users can export all their data via Account Settings → Export My Data." variant="success" />
        <ComplianceCard icon={<Trash2 size={18} className="text-danger" />} title="Right to Erasure"
          description="Deletion requests submitted by users appear below for admin processing." variant="danger" />
        <ComplianceCard icon={<Download size={18} className="text-primary" />} title="Retention Policies"
          description="Configure auto-deletion windows per tenant in the Tenant Management tab." variant="primary" />
      </motion.div>

      <motion.div variants={fadeInUp}>
        <Card className="overflow-hidden">
          <div className="px-4 py-3 border-b border-border flex items-center justify-between">
            <h3 className="text-sm font-medium text-foreground">Pending Deletion Requests</h3>
            <IconButton label="Refresh deletion requests" variant="ghost" size="sm" onClick={load}>
              <RefreshCw size={14} />
            </IconButton>
          </div>

          {loading && (
            <div className="py-2">
              <SkeletonRow columns={3} />
              <SkeletonRow columns={3} />
              <SkeletonRow columns={3} />
            </div>
          )}

          {!loading && requests.length === 0 && (
            <EmptyState icon={Shield} title="No pending deletion requests"
              description="Deletion requests submitted by users will appear here for processing." />
          )}

          {requests.map(req => (
            <div key={req.id} className="flex items-center gap-3 px-4 py-3 border-b border-border last:border-0">
              <div className="flex-1 min-w-0">
                <p className="text-sm text-foreground font-mono truncate">{req.userId}</p>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Requested {new Date(req.requestedAt).toLocaleDateString()}
                </p>
              </div>
              <Badge variant={req.status === 'PENDING' ? 'warning' : req.status === 'COMPLETED' ? 'success' : 'danger'}>
                {req.status}
              </Badge>
              {req.status === 'PENDING' && (
                <Button
                  variant="danger"
                  size="sm"
                  onClick={() => handleProcess(req.id, req.userId)}
                  loading={processingId === req.id}
                  leftIcon={<Trash2 size={12} />}
                >
                  Erase
                </Button>
              )}
            </div>
          ))}
        </Card>
      </motion.div>
    </motion.div>
  );
}

function ComplianceCard({ icon, title, description, variant }: {
  icon: React.ReactNode; title: string; description: string;
  variant: 'success' | 'danger' | 'primary';
}) {
  const bg = {
    success: 'bg-success/10 border-success/20',
    danger: 'bg-danger/10 border-danger/20',
    primary: 'bg-primary/10 border-primary/20',
  }[variant];
  return (
    <div className={cn('border rounded-xl p-4', bg)}>
      <div className="flex items-center gap-2 mb-2">{icon}<span className="text-sm font-medium text-foreground">{title}</span></div>
      <p className="text-xs text-muted-foreground">{description}</p>
    </div>
  );
}
