import React, { useCallback, useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { Users, UserPlus, Shield, AlertCircle } from 'lucide-react';
import { getTenantUsers } from '../../services/tenantService';
import { inviteUser } from '../../services/invitationService';
import type { TenantUser } from '../../types';
import { useAuth } from '../../context/AuthContext';
import { fadeInUp, staggerContainer } from '../../lib/motion';
import PageHeader from '../ui/PageHeader';
import { Card, CardHeader, CardBody } from '../ui/Card';
import Button from '../ui/Button';
import Input from '../ui/Input';
import Badge from '../ui/Badge';
import EmptyState from '../ui/EmptyState';
import { SkeletonRow } from '../ui/Skeleton';
import { useToast } from '../ui/Toast';

export default function UsersPage() {
  const { token, user } = useAuth();
  const toast = useToast();
  const tenantId = user?.tenantId ?? '';

  const [users, setUsers] = useState<TenantUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [inviteEmail, setInviteEmail] = useState('');
  const [inviting, setInviting] = useState(false);

  const load = useCallback(async () => {
    if (!token || !tenantId) return;
    setLoading(true);
    setError('');
    try {
      setUsers(await getTenantUsers(token, tenantId));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load users');
    } finally {
      setLoading(false);
    }
  }, [token, tenantId]);

  useEffect(() => { load(); }, [load]);

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || !inviteEmail) return;
    setInviting(true);
    try {
      await inviteUser(token, inviteEmail);
      toast.success(`Invitation sent to ${inviteEmail}.`);
      setInviteEmail('');
    } catch (e) {
      const detail = (e as { response?: { data?: { error?: string } } })?.response?.data?.error;
      toast.error(detail || 'Failed to send invitation.');
    } finally {
      setInviting(false);
    }
  };

  return (
    <div>
      <PageHeader title="Users" description="Invite users into your tenant. Grant per-document access from the Documents page." />

      <motion.div variants={staggerContainer} initial="hidden" animate="visible" className="space-y-6">
        {/* Invite form */}
        <motion.div variants={fadeInUp}>
          <Card>
            <CardBody>
              <h3 className="text-sm font-semibold text-foreground mb-1 flex items-center gap-2">
                <UserPlus size={16} className="text-primary" /> Invite a user
              </h3>
              <p className="text-xs text-muted-foreground mb-4">Sends an email invitation to join your tenant as a regular user.</p>
              <form onSubmit={handleInvite} className="flex flex-col sm:flex-row gap-3 sm:items-start">
                <div className="flex-1">
                  <Input
                    type="email"
                    aria-label="Email address to invite"
                    value={inviteEmail}
                    onChange={e => setInviteEmail(e.target.value)}
                    required
                    placeholder="user@company.com"
                  />
                </div>
                <Button type="submit" disabled={inviting} loading={inviting} leftIcon={!inviting ? <UserPlus size={14} /> : undefined}>
                  {inviting ? 'Sending…' : 'Send invite'}
                </Button>
              </form>
            </CardBody>
          </Card>
        </motion.div>

        {/* User list */}
        <motion.div variants={fadeInUp}>
          <Card>
            <CardHeader className="flex items-center gap-2">
              <Users size={16} className="text-muted-foreground" />
              <h3 className="text-sm font-semibold text-foreground">Tenant Users</h3>
              {!loading && <span className="ml-auto text-xs text-muted-foreground">{users.length} users</span>}
            </CardHeader>
            {loading ? (
              <div className="divide-y divide-border">{[0, 1, 2].map(i => <SkeletonRow key={i} columns={3} />)}</div>
            ) : error ? (
              <div className="p-6 text-center text-danger flex items-center justify-center gap-2"><AlertCircle className="w-5 h-5" />{error}</div>
            ) : users.length === 0 ? (
              <EmptyState icon={Users} title="No users yet" description="Invite your first user above to get started." />
            ) : (
              <div className="divide-y divide-border">
                {users.map(u => (
                  <div key={u.userId} className="flex items-center gap-3 px-5 py-3">
                    <div className="w-8 h-8 rounded-full bg-primary flex items-center justify-center text-primary-foreground text-sm font-bold flex-shrink-0">
                      {u.username.charAt(0).toUpperCase()}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-foreground">{u.username}</span>
                        <Badge variant="neutral" className={u.role === 'ADMIN' ? 'bg-accent/10 text-accent' : undefined}>
                          {u.role}
                        </Badge>
                        {u.role === 'ADMIN' && <Shield size={12} className="text-accent" />}
                      </div>
                      <span className="text-xs text-muted-foreground truncate">{u.email}</span>
                    </div>
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
