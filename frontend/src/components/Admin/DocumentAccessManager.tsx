import { useCallback, useEffect, useState } from 'react';
import { UserPlus, Users, Trash2, AlertCircle, Lock } from 'lucide-react';
import {
  listGrantees, grantDocumentAccess, revokeDocumentAccess,
  listGroupGrantees, grantDocumentAccessToGroup, revokeDocumentAccessFromGroup,
} from '../../services/documentAccessService';
import { useTenantUsers } from '../../hooks/useTenantUsers';
import { useGroups } from '../../hooks/useGroups';
import type { DocumentGrantee, DocumentGroupGrantee, Group, TenantUser } from '../../types';
import Combobox from '../ui/Combobox';
import IconButton from '../ui/IconButton';
import EmptyState from '../ui/EmptyState';
import { SkeletonText } from '../ui/Skeleton';
import { useToast } from '../ui/Toast';

export default function DocumentAccessManager({
  token, documentId,
}: {
  token: string;
  documentId: string;
}) {
  const toast = useToast();
  const [grantees, setGrantees] = useState<DocumentGrantee[] | null>(null);
  const [error, setError] = useState('');
  const [userQuery, setUserQuery] = useState('');
  const [grantingUserId, setGrantingUserId] = useState<string | null>(null);
  const [revokingUserId, setRevokingUserId] = useState<string | null>(null);

  const [groupGrantees, setGroupGrantees] = useState<DocumentGroupGrantee[] | null>(null);
  const [groupError, setGroupError] = useState('');
  const [groupQuery, setGroupQuery] = useState('');
  const [grantingGroupId, setGrantingGroupId] = useState<string | null>(null);
  const [revokingGroupId, setRevokingGroupId] = useState<string | null>(null);

  // Debounced so typeahead doesn't fire a search request on every keystroke.
  const [debouncedUserQuery, setDebouncedUserQuery] = useState('');
  const [debouncedGroupQuery, setDebouncedGroupQuery] = useState('');
  useEffect(() => {
    const handle = setTimeout(() => setDebouncedUserQuery(userQuery.trim()), 250);
    return () => clearTimeout(handle);
  }, [userQuery]);
  useEffect(() => {
    const handle = setTimeout(() => setDebouncedGroupQuery(groupQuery.trim()), 250);
    return () => clearTimeout(handle);
  }, [groupQuery]);

  const { data: userResults, isFetching: searchingUsers } = useTenantUsers({ q: debouncedUserQuery, size: 20 });
  const { data: groupResults = [], isFetching: searchingGroups } = useGroups(debouncedGroupQuery);

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
  const grantableUsers = (userResults?.content ?? []).filter(u => u.role === 'USER' && !grantedIds.has(u.userId));

  const grantedGroupIds = new Set((groupGrantees ?? []).map(g => g.groupId));
  const grantableGroups = groupResults.filter(g => !grantedGroupIds.has(g.id));

  const handleGrant = async (user: TenantUser) => {
    setGrantingUserId(user.userId);
    try {
      await grantDocumentAccess(token, documentId, user.userId);
      await load();
      toast.success('Access granted.');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to grant access.');
    } finally {
      setGrantingUserId(null);
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

  const handleGrantGroup = async (group: Group) => {
    setGrantingGroupId(group.id);
    try {
      await grantDocumentAccessToGroup(token, documentId, group.id);
      await loadGroups();
      toast.success('Group access granted.');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to grant group access.');
    } finally {
      setGrantingGroupId(null);
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
        <Combobox
          query={userQuery}
          onQueryChange={setUserQuery}
          items={grantableUsers}
          getKey={u => u.userId}
          getLabel={u => `${u.username} (${u.email})`}
          onSelect={handleGrant}
          placeholder="Search users to grant access…"
          loading={searchingUsers || !!grantingUserId}
        />

        {error && (
          <div className="flex items-center gap-2 text-xs text-danger bg-danger/10 rounded-lg px-3 py-2">
            <AlertCircle size={13} className="flex-shrink-0" />{error}
          </div>
        )}

        {grantees === null ? (
          <SkeletonText lines={2} />
        ) : grantees.length === 0 ? (
          <EmptyState icon={UserPlus} title="No users granted access yet" description="Search for a user above to let them see this document in chat." />
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
        <Combobox
          query={groupQuery}
          onQueryChange={setGroupQuery}
          items={grantableGroups}
          getKey={g => g.id}
          getLabel={g => `${g.name} (${g.memberCount} member${g.memberCount !== 1 ? 's' : ''})`}
          onSelect={handleGrantGroup}
          placeholder="Search groups to grant access…"
          loading={searchingGroups || !!grantingGroupId}
        />

        {groupError && (
          <div className="flex items-center gap-2 text-xs text-danger bg-danger/10 rounded-lg px-3 py-2">
            <AlertCircle size={13} className="flex-shrink-0" />{groupError}
          </div>
        )}

        {groupGrantees === null ? (
          <SkeletonText lines={2} />
        ) : groupGrantees.length === 0 ? (
          <EmptyState icon={Users} title="No groups granted access yet" description="Search for a group above to let its members see this document in chat." />
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
