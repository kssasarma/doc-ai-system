import React, { useCallback, useEffect, useState } from 'react';
import { Users, Plus, Trash2, AlertCircle, ChevronDown, ChevronRight, UserPlus } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { listGroups, createGroup, deleteGroup, listGroupMembers, addGroupMember, removeGroupMember } from '../../services/groupService';
import { getTenantUsers } from '../../services/tenantService';
import type { Group, GroupMember, TenantUser } from '../../types';
import { useAuth } from '../../context/AuthContext';
import { fadeInUp, staggerContainer, EASE_OUT } from '../../lib/motion';
import PageHeader from '../ui/PageHeader';
import { Card, CardHeader } from '../ui/Card';
import Button from '../ui/Button';
import IconButton from '../ui/IconButton';
import Input from '../ui/Input';
import Select from '../ui/Select';
import EmptyState from '../ui/EmptyState';
import { Skeleton, SkeletonText } from '../ui/Skeleton';
import { useToast } from '../ui/Toast';

export default function GroupsPage() {
  const { token, user } = useAuth();
  const tenantId = user?.tenantId ?? '';
  const toast = useToast();

  const [groups, setGroups] = useState<Group[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [tenantUsers, setTenantUsers] = useState<TenantUser[]>([]);

  const [newGroupName, setNewGroupName] = useState('');
  const [creating, setCreating] = useState(false);
  const [createMsg, setCreateMsg] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const [expandedGroupId, setExpandedGroupId] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError('');
    try {
      setGroups(await listGroups(token));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load groups');
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => { load(); }, [load]);

  useEffect(() => {
    if (!token || !tenantId) return;
    getTenantUsers(token, tenantId).then(setTenantUsers).catch(() => {});
  }, [token, tenantId]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || !newGroupName.trim()) return;
    setCreating(true);
    setCreateMsg(null);
    try {
      await createGroup(token, newGroupName.trim());
      setNewGroupName('');
      setCreateMsg({ type: 'success', text: 'Group created.' });
      await load();
    } catch (e) {
      const detail = (e as { response?: { data?: { error?: string } } })?.response?.data?.error;
      setCreateMsg({ type: 'error', text: detail || 'Failed to create group.' });
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = async (groupId: string) => {
    if (!token) return;
    try {
      await deleteGroup(token, groupId);
      if (expandedGroupId === groupId) setExpandedGroupId(null);
      await load();
      toast.success('Group deleted.');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to delete group.');
    }
  };

  return (
    <div>
      <PageHeader
        title="Groups"
        description="Grant several users access to a document in one action instead of one grant per person — add users to a group, then grant the group access from a document's Access panel."
      />

      <motion.div variants={staggerContainer} initial="hidden" animate="visible" className="space-y-6">
        {/* Create group */}
        <motion.div variants={fadeInUp}>
          <Card className="p-5">
            <h3 className="text-sm font-semibold text-foreground mb-1 flex items-center gap-2">
              <Plus size={16} className="text-primary" /> Create a group
            </h3>
            <form onSubmit={handleCreate} className="flex flex-col sm:flex-row gap-3 mt-3">
              <div className="flex-1">
                <Input
                  type="text"
                  value={newGroupName}
                  onChange={e => setNewGroupName(e.target.value)}
                  required
                  placeholder="e.g. Support Team"
                />
              </div>
              <Button
                type="submit"
                disabled={!newGroupName.trim()}
                loading={creating}
                leftIcon={!creating ? <Plus size={14} /> : undefined}
              >
                {creating ? 'Creating…' : 'Create group'}
              </Button>
            </form>
            {createMsg && (
              <div
                className={
                  createMsg.type === 'success'
                    ? 'flex items-center gap-2 text-xs mt-3 rounded-lg px-3 py-2 bg-success/10 text-success'
                    : 'flex items-center gap-2 text-xs mt-3 rounded-lg px-3 py-2 bg-danger/10 text-danger'
                }
              >
                <AlertCircle size={14} className="flex-shrink-0" />
                {createMsg.text}
              </div>
            )}
          </Card>
        </motion.div>

        {/* Group list */}
        <motion.div variants={fadeInUp}>
          <Card>
            <CardHeader className="flex items-center gap-2">
              <Users size={16} className="text-muted-foreground" />
              <h3 className="text-sm font-semibold text-foreground">Tenant Groups</h3>
              {!loading && <span className="ml-auto text-xs text-muted-foreground">{groups.length} groups</span>}
            </CardHeader>
            {loading ? (
              <div className="divide-y divide-border">
                {[0, 1, 2].map(i => (
                  <div key={i} className="flex items-center gap-3 px-5 py-3">
                    <Skeleton className="w-8 h-8 rounded-full flex-shrink-0" />
                    <div className="flex-1 space-y-1.5">
                      <Skeleton className="h-3.5 w-1/3" />
                      <Skeleton className="h-3 w-1/5" />
                    </div>
                  </div>
                ))}
              </div>
            ) : error ? (
              <div className="p-6 text-center text-danger flex items-center justify-center gap-2">
                <AlertCircle className="w-5 h-5" />{error}
              </div>
            ) : groups.length === 0 ? (
              <EmptyState
                icon={Users}
                title="No groups yet"
                description="Create your first group above to start managing document access in bulk."
              />
            ) : (
              <div className="divide-y divide-border">
                {groups.map(g => (
                  <div key={g.id}>
                    <div className="flex items-center gap-2 px-5 py-3 hover:bg-surface-hover transition-colors">
                      <button
                        onClick={() => setExpandedGroupId(expandedGroupId === g.id ? null : g.id)}
                        className="flex items-center gap-3 flex-1 min-w-0 text-left"
                      >
                        <div className="w-8 h-8 rounded-full bg-accent flex items-center justify-center text-white text-sm font-bold flex-shrink-0">
                          {g.name.charAt(0).toUpperCase()}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="text-sm font-medium text-foreground">{g.name}</div>
                          <div className="text-xs text-muted-foreground">{g.memberCount} member{g.memberCount !== 1 ? 's' : ''}</div>
                        </div>
                        {expandedGroupId === g.id ? (
                          <ChevronDown size={14} className="text-muted-foreground flex-shrink-0" />
                        ) : (
                          <ChevronRight size={14} className="text-muted-foreground flex-shrink-0" />
                        )}
                      </button>
                      <IconButton label="Delete group" variant="danger" size="sm" onClick={() => handleDelete(g.id)}>
                        <Trash2 size={14} />
                      </IconButton>
                    </div>

                    <AnimatePresence initial={false}>
                      {expandedGroupId === g.id && (
                        <motion.div
                          key="panel"
                          initial={{ height: 0, opacity: 0 }}
                          animate={{ height: 'auto', opacity: 1 }}
                          exit={{ height: 0, opacity: 0 }}
                          transition={{ duration: 0.2, ease: EASE_OUT }}
                          className="overflow-hidden"
                        >
                          <div className="bg-muted px-5 py-4 border-t border-border">
                            <GroupMembersPanel
                              token={token!}
                              groupId={g.id}
                              tenantUsers={tenantUsers}
                              onMembershipChanged={load}
                            />
                          </div>
                        </motion.div>
                      )}
                    </AnimatePresence>
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

function GroupMembersPanel({
  token, groupId, tenantUsers, onMembershipChanged,
}: {
  token: string;
  groupId: string;
  tenantUsers: TenantUser[];
  onMembershipChanged: () => void;
}) {
  const toast = useToast();
  const [members, setMembers] = useState<GroupMember[] | null>(null);
  const [error, setError] = useState('');
  const [selectedUserId, setSelectedUserId] = useState('');
  const [adding, setAdding] = useState(false);
  const [removingUserId, setRemovingUserId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError('');
    try {
      setMembers(await listGroupMembers(token, groupId));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load members');
    }
  }, [token, groupId]);

  useEffect(() => { load(); }, [load]);

  const memberIds = new Set((members ?? []).map(m => m.userId));
  const addableUsers = tenantUsers.filter(u => !memberIds.has(u.userId));

  const handleAdd = async () => {
    if (!selectedUserId) return;
    setAdding(true);
    setError('');
    try {
      await addGroupMember(token, groupId, selectedUserId);
      setSelectedUserId('');
      await load();
      onMembershipChanged();
      toast.success('Member added.');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to add member');
    } finally {
      setAdding(false);
    }
  };

  const handleRemove = async (userId: string) => {
    setRemovingUserId(userId);
    try {
      await removeGroupMember(token, groupId, userId);
      await load();
      onMembershipChanged();
      toast.success('Member removed.');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to remove member');
    } finally {
      setRemovingUserId(null);
    }
  };

  return (
    <div className="space-y-3">
      <div className="flex flex-col sm:flex-row gap-2">
        <div className="flex-1">
          <Select value={selectedUserId} onChange={e => setSelectedUserId(e.target.value)}>
            <option value="">
              {addableUsers.length === 0 ? 'No more users to add' : 'Select a user to add…'}
            </option>
            {addableUsers.map(u => (
              <option key={u.userId} value={u.userId}>{u.username} ({u.email})</option>
            ))}
          </Select>
        </div>
        <Button
          variant="secondary"
          size="md"
          onClick={handleAdd}
          disabled={!selectedUserId}
          loading={adding}
          leftIcon={!adding ? <UserPlus size={14} /> : undefined}
          className="whitespace-nowrap"
        >
          Add
        </Button>
      </div>

      {error && (
        <div className="flex items-center gap-2 text-xs text-danger bg-danger/10 rounded-lg px-3 py-2">
          <AlertCircle size={13} className="flex-shrink-0" />{error}
        </div>
      )}

      {members === null ? (
        <SkeletonText lines={2} />
      ) : members.length === 0 ? (
        <EmptyState icon={UserPlus} title="No members yet" description="Add a user above to give them access via this group." />
      ) : (
        <div className="space-y-1.5">
          {members.map(m => (
            <div key={m.userId} className="flex items-center justify-between bg-surface border border-border rounded-lg px-3 py-2">
              <div>
                <span className="text-sm text-foreground">{m.username}</span>
                <span className="text-xs text-muted-foreground ml-2">{m.email}</span>
              </div>
              <IconButton
                label="Remove from group"
                variant="danger"
                size="sm"
                onClick={() => handleRemove(m.userId)}
                disabled={removingUserId === m.userId}
              >
                <Trash2 size={14} />
              </IconButton>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
