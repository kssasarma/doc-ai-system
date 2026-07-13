import React, { useCallback, useEffect, useState } from 'react';
import { UserPlus, Users, Trash2, AlertCircle, Lock } from 'lucide-react';
import {
  listGrantees, grantDocumentAccess, revokeDocumentAccess,
  listGroupGrantees, grantDocumentAccessToGroup, revokeDocumentAccessFromGroup,
} from '../../services/documentAccessService';
import type { DocumentGrantee, DocumentGroupGrantee, Group, TenantUser } from '../../types';
import Button from '../ui/Button';
import IconButton from '../ui/IconButton';
import Select from '../ui/Select';
import EmptyState from '../ui/EmptyState';
import { SkeletonText } from '../ui/Skeleton';
import { useToast } from '../ui/Toast';

export default function DocumentAccessManager({
  token, documentId, tenantUsers, groups,
}: {
  token: string;
  documentId: string;
  tenantUsers: TenantUser[];
  groups: Group[];
}) {
  const toast = useToast();
  const [grantees, setGrantees] = useState<DocumentGrantee[] | null>(null);
  const [error, setError] = useState('');
  const [selectedUserId, setSelectedUserId] = useState('');
  const [granting, setGranting] = useState(false);
  const [revokingUserId, setRevokingUserId] = useState<string | null>(null);

  const [groupGrantees, setGroupGrantees] = useState<DocumentGroupGrantee[] | null>(null);
  const [groupError, setGroupError] = useState('');
  const [selectedGroupId, setSelectedGroupId] = useState('');
  const [grantingGroup, setGrantingGroup] = useState(false);
  const [revokingGroupId, setRevokingGroupId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError('');
    try {
      setGrantees(await listGrantees(token, documentId));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load access grants');
    }
  }, [token, documentId]);

  const loadGroups = useCallback(async () => {
    setGroupError('');
    try {
      setGroupGrantees(await listGroupGrantees(token, documentId));
    } catch (e) {
      setGroupError(e instanceof Error ? e.message : 'Failed to load group access grants');
    }
  }, [token, documentId]);

  useEffect(() => { load(); }, [load]);
  useEffect(() => { loadGroups(); }, [loadGroups]);

  const grantedIds = new Set((grantees ?? []).map(g => g.userId));
  const grantableUsers = tenantUsers.filter(u => u.role === 'USER' && !grantedIds.has(u.userId));

  const grantedGroupIds = new Set((groupGrantees ?? []).map(g => g.groupId));
  const grantableGroups = groups.filter(g => !grantedGroupIds.has(g.id));

  const handleGrant = async () => {
    if (!selectedUserId) return;
    setGranting(true);
    try {
      await grantDocumentAccess(token, documentId, selectedUserId);
      setSelectedUserId('');
      await load();
      toast.success('Access granted.');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to grant access.');
    } finally {
      setGranting(false);
    }
  };

  const handleRevoke = async (userId: string) => {
    setRevokingUserId(userId);
    try {
      await revokeDocumentAccess(token, documentId, userId);
      await load();
      toast.success('Access revoked.');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to revoke access.');
    } finally {
      setRevokingUserId(null);
    }
  };

  const handleGrantGroup = async () => {
    if (!selectedGroupId) return;
    setGrantingGroup(true);
    try {
      await grantDocumentAccessToGroup(token, documentId, selectedGroupId);
      setSelectedGroupId('');
      await loadGroups();
      toast.success('Group access granted.');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to grant group access.');
    } finally {
      setGrantingGroup(false);
    }
  };

  const handleRevokeGroup = async (groupId: string) => {
    setRevokingGroupId(groupId);
    try {
      await revokeDocumentAccessFromGroup(token, documentId, groupId);
      await loadGroups();
      toast.success('Group access revoked.');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to revoke group access.');
    } finally {
      setRevokingGroupId(null);
    }
  };

  return (
    <div className="space-y-5">
      <div className="flex items-start gap-2 text-xs text-muted-foreground">
        <Lock size={13} className="flex-shrink-0 mt-0.5" />
        <span>Only users granted access below — directly or via a group — (plus any tenant admin) can see this document in chat.</span>
      </div>

      {/* Per-user grants */}
      <div className="space-y-3">
        <h4 className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Users</h4>
        <div className="flex flex-col sm:flex-row gap-2">
          <div className="flex-1">
            <Select aria-label="Select a user to grant access" value={selectedUserId} onChange={e => setSelectedUserId(e.target.value)}>
              <option value="">
                {grantableUsers.length === 0 ? 'No more users to grant' : 'Select a user to grant access…'}
              </option>
              {grantableUsers.map(u => (
                <option key={u.userId} value={u.userId}>{u.username} ({u.email})</option>
              ))}
            </Select>
          </div>
          <Button
            variant="secondary"
            onClick={handleGrant}
            disabled={!selectedUserId}
            loading={granting}
            leftIcon={!granting ? <UserPlus size={14} /> : undefined}
            className="whitespace-nowrap"
          >
            Grant
          </Button>
        </div>

        {error && (
          <div className="flex items-center gap-2 text-xs text-danger bg-danger/10 rounded-lg px-3 py-2">
            <AlertCircle size={13} className="flex-shrink-0" />{error}
          </div>
        )}

        {grantees === null ? (
          <SkeletonText lines={2} />
        ) : grantees.length === 0 ? (
          <EmptyState icon={UserPlus} title="No users granted access yet" description="Grant a user access above to let them see this document in chat." />
        ) : (
          <div className="space-y-1.5">
            {grantees.map(g => (
              <div key={g.userId} className="flex items-center justify-between bg-muted border border-border rounded-lg px-3 py-2">
                <span className="text-sm text-foreground">{g.username}</span>
                <IconButton
                  label="Revoke access"
                  variant="danger"
                  size="sm"
                  onClick={() => handleRevoke(g.userId)}
                  disabled={revokingUserId === g.userId}
                >
                  <Trash2 size={14} />
                </IconButton>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Per-group grants */}
      <div className="space-y-3 pt-4 border-t border-border">
        <h4 className="text-xs font-semibold text-muted-foreground uppercase tracking-wide flex items-center gap-1.5">
          <Users size={12} /> Groups
        </h4>
        <div className="flex flex-col sm:flex-row gap-2">
          <div className="flex-1">
            <Select aria-label="Select a group to grant access" value={selectedGroupId} onChange={e => setSelectedGroupId(e.target.value)}>
              <option value="">
                {groups.length === 0 ? 'No groups exist yet' : grantableGroups.length === 0 ? 'No more groups to grant' : 'Select a group to grant access…'}
              </option>
              {grantableGroups.map(g => (
                <option key={g.id} value={g.id}>{g.name} ({g.memberCount} member{g.memberCount !== 1 ? 's' : ''})</option>
              ))}
            </Select>
          </div>
          <Button
            variant="secondary"
            onClick={handleGrantGroup}
            disabled={!selectedGroupId}
            loading={grantingGroup}
            leftIcon={!grantingGroup ? <UserPlus size={14} /> : undefined}
            className="whitespace-nowrap"
          >
            Grant
          </Button>
        </div>

        {groupError && (
          <div className="flex items-center gap-2 text-xs text-danger bg-danger/10 rounded-lg px-3 py-2">
            <AlertCircle size={13} className="flex-shrink-0" />{groupError}
          </div>
        )}

        {groupGrantees === null ? (
          <SkeletonText lines={2} />
        ) : groupGrantees.length === 0 ? (
          <EmptyState icon={Users} title="No groups granted access yet" description="Grant a group access above to let its members see this document in chat." />
        ) : (
          <div className="space-y-1.5">
            {groupGrantees.map(g => (
              <div key={g.groupId} className="flex items-center justify-between bg-muted border border-border rounded-lg px-3 py-2">
                <span className="text-sm text-foreground">{g.groupName}</span>
                <IconButton
                  label="Revoke group access"
                  variant="danger"
                  size="sm"
                  onClick={() => handleRevokeGroup(g.groupId)}
                  disabled={revokingGroupId === g.groupId}
                >
                  <Trash2 size={14} />
                </IconButton>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
