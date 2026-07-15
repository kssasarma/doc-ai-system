import React, { useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import {
  Users, UserPlus, Shield, AlertCircle, Search, ChevronLeft, ChevronRight,
  UserX, UserCheck, Trash2, Mail, Clock, X,
} from 'lucide-react';
import { inviteUser, listPendingInvitations, revokeInvitation } from '../../services/invitationService';
import { changeUserRole, deactivateUser, reactivateUser, eraseUser } from '../../services/tenantService';
import { useTenantUsers } from '../../hooks/useTenantUsers';
import type { TenantUser, Role } from '../../types';
import { useAuth } from '../../context/AuthContext';
import { fadeInUp, staggerContainer } from '../../lib/motion';
import { cn } from '../../lib/cn';
import PageHeader from '../ui/PageHeader';
import { Card, CardHeader, CardBody } from '../ui/Card';
import Button from '../ui/Button';
import IconButton from '../ui/IconButton';
import Input from '../ui/Input';
import Badge from '../ui/Badge';
import EmptyState from '../ui/EmptyState';
import { SkeletonRow } from '../ui/Skeleton';
import { useToast } from '../ui/Toast';
import { useConfirm } from '../ui/ConfirmDialog';

const PAGE_SIZE = 20;

export default function UsersPage() {
  const { token, user: currentUser } = useAuth();
  const toast = useToast();
  const confirm = useConfirm();
  const queryClient = useQueryClient();

  const [inviteEmail, setInviteEmail] = useState('');
  const [inviting, setInviting] = useState(false);

  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [page, setPage] = useState(0);
  useEffect(() => {
    const handle = setTimeout(() => { setDebouncedQuery(searchQuery.trim()); setPage(0); }, 300);
    return () => clearTimeout(handle);
  }, [searchQuery]);

  const { data, isLoading, isError } = useTenantUsers({ q: debouncedQuery, page, size: PAGE_SIZE });
  const users = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;
  const error = isError ? 'Failed to load users' : '';

  const pendingQuery = useQuery({
    queryKey: ['pending-invitations'],
    queryFn: () => listPendingInvitations(token!),
    enabled: !!token,
  });
  const pending = pendingQuery.data ?? [];

  const [actingUserId, setActingUserId] = useState<string | null>(null);
  const [revokingId, setRevokingId] = useState<string | null>(null);

  const reloadUsers = () => queryClient.invalidateQueries({ queryKey: ['tenant-users'] });
  const reloadPending = () => queryClient.invalidateQueries({ queryKey: ['pending-invitations'] });

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || !inviteEmail) return;
    setInviting(true);
    try {
      await inviteUser(token, inviteEmail);
      toast.success(`Invitation sent to ${inviteEmail}.`);
      setInviteEmail('');
      reloadPending();
    } catch (e) {
      const detail = (e as { response?: { data?: { error?: string } } })?.response?.data?.error;
      toast.error(detail || 'Failed to send invitation.');
    } finally {
      setInviting(false);
    }
  };

  const handleRevoke = async (invitationId: string) => {
    if (!token) return;
    setRevokingId(invitationId);
    try {
      await revokeInvitation(token, invitationId);
      toast.success('Invitation revoked.');
      reloadPending();
    } catch (e) {
      const detail = (e as { response?: { data?: { error?: string } } })?.response?.data?.error;
      toast.error(detail || 'Failed to revoke invitation.');
    } finally {
      setRevokingId(null);
    }
  };

  const handleRoleChange = async (targetUser: TenantUser, role: Role) => {
    if (!token || role === targetUser.role) return;
    setActingUserId(targetUser.userId);
    try {
      await changeUserRole(token, targetUser.userId, role as 'ADMIN' | 'USER');
      toast.success(`${targetUser.username} is now ${role}.`);
      reloadUsers();
    } catch (e) {
      const detail = (e as { response?: { data?: { error?: string } } })?.response?.data?.error;
      toast.error(detail || 'Failed to change role.');
    } finally {
      setActingUserId(null);
    }
  };

  const handleToggleActive = async (targetUser: TenantUser) => {
    if (!token) return;
    setActingUserId(targetUser.userId);
    try {
      if (targetUser.active) {
        await deactivateUser(token, targetUser.userId);
        toast.success(`${targetUser.username} deactivated.`);
      } else {
        await reactivateUser(token, targetUser.userId);
        toast.success(`${targetUser.username} reactivated.`);
      }
      reloadUsers();
    } catch (e) {
      const detail = (e as { response?: { data?: { error?: string } } })?.response?.data?.error;
      toast.error(detail || 'Failed to update account status.');
    } finally {
      setActingUserId(null);
    }
  };

  const handleErase = async (targetUser: TenantUser) => {
    if (!token) return;
    const confirmed = await confirm({
      title: 'Erase account',
      message: `Permanently erase "${targetUser.username}"'s account (GDPR erasure)? This anonymizes their data and cannot be undone.`,
      confirmLabel: 'Erase',
      danger: true,
    });
    if (!confirmed) return;
    setActingUserId(targetUser.userId);
    try {
      await eraseUser(token, targetUser.userId);
      toast.success(`${targetUser.username} erased.`);
      reloadUsers();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to erase user.');
    } finally {
      setActingUserId(null);
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

        {/* Pending invitations */}
        {pending.length > 0 && (
          <motion.div variants={fadeInUp}>
            <Card>
              <CardHeader className="flex items-center gap-2">
                <Mail size={16} className="text-muted-foreground" />
                <h3 className="text-sm font-semibold text-foreground">Pending Invitations</h3>
                <span className="ml-auto text-xs text-muted-foreground">{pending.length} pending</span>
              </CardHeader>
              <div className="divide-y divide-border">
                {pending.map(inv => (
                  <div key={inv.id} className="flex items-center gap-3 px-5 py-3">
                    <Clock size={14} className="text-muted-foreground flex-shrink-0" />
                    <div className="flex-1 min-w-0">
                      <span className="text-sm text-foreground">{inv.email}</span>
                      <span className="text-xs text-muted-foreground ml-2">
                        invited as {inv.role} · expires {new Date(inv.expiresAt).toLocaleDateString()}
                      </span>
                    </div>
                    <IconButton
                      label="Revoke invitation"
                      variant="danger"
                      size="sm"
                      onClick={() => handleRevoke(inv.id)}
                      disabled={revokingId === inv.id}
                    >
                      <X size={14} />
                    </IconButton>
                  </div>
                ))}
              </div>
            </Card>
          </motion.div>
        )}

        {/* User list */}
        <motion.div variants={fadeInUp}>
          <Card>
            <CardHeader className="flex items-center gap-2">
              <Users size={16} className="text-muted-foreground" />
              <h3 className="text-sm font-semibold text-foreground">Tenant Users</h3>
              {!isLoading && <span className="ml-auto text-xs text-muted-foreground">{totalElements} users</span>}
            </CardHeader>
            <div className="px-5 pt-4">
              <div className="relative">
                <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none" />
                <Input
                  type="text"
                  aria-label="Search users"
                  placeholder="Search by username or email…"
                  value={searchQuery}
                  onChange={e => setSearchQuery(e.target.value)}
                  className="pl-9"
                />
              </div>
            </div>
            {isLoading ? (
              <div className="divide-y divide-border mt-2">{[0, 1, 2].map(i => <SkeletonRow key={i} columns={3} />)}</div>
            ) : error ? (
              <div className="p-6 text-center text-danger flex items-center justify-center gap-2"><AlertCircle className="w-5 h-5" />{error}</div>
            ) : users.length === 0 ? (
              <EmptyState icon={Users} title={debouncedQuery ? 'No users found' : 'No users yet'} description={debouncedQuery ? 'Try a different search term.' : 'Invite your first user above to get started.'} />
            ) : (
              <div className="divide-y divide-border mt-2">
                {users.map(u => {
                  const isSelf = u.userId === currentUser?.userId;
                  const acting = actingUserId === u.userId;
                  return (
                    <div key={u.userId} className={cn('flex items-center gap-3 px-5 py-3', !u.active && 'opacity-60')}>
                      <div className="w-8 h-8 rounded-full bg-primary flex items-center justify-center text-primary-foreground text-sm font-bold flex-shrink-0">
                        {u.username.charAt(0).toUpperCase()}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="text-sm font-medium text-foreground">{u.username}</span>
                          {!u.active && <Badge variant="warning">Deactivated</Badge>}
                          {u.role === 'ADMIN' && <Shield size={12} className="text-accent" />}
                        </div>
                        <span className="text-xs text-muted-foreground truncate">{u.email}</span>
                      </div>
                      <div className="flex items-center gap-1.5 flex-shrink-0">
                        <select
                          aria-label={`Change role for ${u.username}`}
                          value={u.role}
                          disabled={isSelf || acting}
                          onChange={e => handleRoleChange(u, e.target.value as Role)}
                          className="text-xs border border-border bg-surface rounded-lg px-2 py-1.5 text-foreground disabled:opacity-50"
                        >
                          <option value="USER">USER</option>
                          <option value="ADMIN">ADMIN</option>
                        </select>
                        <IconButton
                          label={u.active ? 'Deactivate account' : 'Reactivate account'}
                          variant="ghost"
                          size="sm"
                          disabled={isSelf || acting}
                          onClick={() => handleToggleActive(u)}
                        >
                          {u.active ? <UserX size={14} /> : <UserCheck size={14} />}
                        </IconButton>
                        <IconButton
                          label="Permanently erase account"
                          variant="danger"
                          size="sm"
                          disabled={isSelf || acting}
                          onClick={() => handleErase(u)}
                        >
                          <Trash2 size={14} />
                        </IconButton>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}

            {totalPages > 1 && (
              <div className="px-5 py-3 border-t border-border flex items-center justify-between">
                <span className="text-xs text-muted-foreground">Page {page + 1} of {totalPages}</span>
                <div className="flex items-center gap-2">
                  <IconButton label="Previous page" variant="ghost" size="sm" onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}>
                    <ChevronLeft size={16} />
                  </IconButton>
                  <IconButton label="Next page" variant="ghost" size="sm" onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}>
                    <ChevronRight size={16} />
                  </IconButton>
                </div>
              </div>
            )}
          </Card>
        </motion.div>
      </motion.div>
    </div>
  );
}

