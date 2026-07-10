import React, { useCallback, useEffect, useState } from 'react';
import { Users, Plus, Trash2, AlertCircle, CheckCircle, RefreshCw, ChevronDown, ChevronRight, UserPlus } from 'lucide-react';
import { listGroups, createGroup, deleteGroup, listGroupMembers, addGroupMember, removeGroupMember } from '../../services/groupService';
import { getTenantUsers } from '../../services/tenantService';
import type { Group, GroupMember, TenantUser } from '../../types';
import { useAuth } from '../../context/AuthContext';

export default function GroupsPage() {
  const { token, user } = useAuth();
  const tenantId = user?.tenantId ?? '';

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
    } catch { /* surfaced via the list not changing; acceptable for a destructive low-risk action */ }
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-gray-800 mb-1">Groups</h2>
        <p className="text-sm text-gray-500">
          Grant several users access to a document in one action instead of one grant per person — add users to a group, then grant the group access from a document's Access panel.
        </p>
      </div>

      {/* Create group */}
      <div className="bg-white rounded-xl border border-gray-200 p-5">
        <h3 className="text-sm font-semibold text-gray-700 mb-1 flex items-center gap-2">
          <Plus size={16} className="text-blue-500" /> Create a group
        </h3>
        <form onSubmit={handleCreate} className="flex flex-col sm:flex-row gap-3 mt-3">
          <input
            type="text"
            value={newGroupName}
            onChange={e => setNewGroupName(e.target.value)}
            required
            placeholder="e.g. Support Team"
            className="flex-1 text-sm border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <button
            type="submit"
            disabled={creating || !newGroupName.trim()}
            className="flex items-center justify-center gap-1.5 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-60 transition-colors"
          >
            {creating ? <RefreshCw size={14} className="animate-spin" /> : <Plus size={14} />}
            {creating ? 'Creating…' : 'Create group'}
          </button>
        </form>
        {createMsg && (
          <div className={`flex items-center gap-2 text-xs mt-3 rounded-lg px-3 py-2 ${createMsg.type === 'success' ? 'text-green-700 bg-green-50' : 'text-red-600 bg-red-50'}`}>
            {createMsg.type === 'success' ? <CheckCircle size={14} className="flex-shrink-0" /> : <AlertCircle size={14} className="flex-shrink-0" />}
            {createMsg.text}
          </div>
        )}
      </div>

      {/* Group list */}
      <div className="bg-white rounded-xl border border-gray-200">
        <div className="px-5 py-4 border-b border-gray-100 flex items-center gap-2">
          <Users size={16} className="text-gray-400" />
          <h3 className="text-sm font-semibold text-gray-700">Tenant Groups</h3>
          {!loading && <span className="ml-auto text-xs text-gray-400">{groups.length} groups</span>}
        </div>
        {loading ? (
          <div className="p-12 text-center text-gray-400">Loading groups…</div>
        ) : error ? (
          <div className="p-6 text-center text-red-500 flex items-center justify-center gap-2"><AlertCircle className="w-5 h-5" />{error}</div>
        ) : groups.length === 0 ? (
          <div className="p-12 text-center text-gray-400">No groups yet. Create your first group above.</div>
        ) : (
          <div className="divide-y divide-gray-100">
            {groups.map(g => (
              <div key={g.id}>
                <button
                  onClick={() => setExpandedGroupId(expandedGroupId === g.id ? null : g.id)}
                  className="w-full flex items-center gap-3 px-5 py-3 hover:bg-gray-50 transition-colors text-left"
                >
                  <div className="w-8 h-8 rounded-full bg-indigo-600 flex items-center justify-center text-white text-sm font-bold flex-shrink-0">
                    {g.name.charAt(0).toUpperCase()}
                  </div>
                  <div className="flex-1 min-w-0">
                    <span className="text-sm font-medium text-gray-900">{g.name}</span>
                    <div className="text-xs text-gray-400">{g.memberCount} member{g.memberCount !== 1 ? 's' : ''}</div>
                  </div>
                  <button
                    onClick={e => { e.stopPropagation(); handleDelete(g.id); }}
                    title="Delete group"
                    className="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors flex-shrink-0"
                  >
                    <Trash2 size={14} />
                  </button>
                  {expandedGroupId === g.id ? <ChevronDown size={14} className="text-gray-400 flex-shrink-0" /> : <ChevronRight size={14} className="text-gray-400 flex-shrink-0" />}
                </button>

                {expandedGroupId === g.id && (
                  <div className="bg-gray-50 px-5 py-4 border-t border-gray-100">
                    <GroupMembersPanel
                      token={token!}
                      groupId={g.id}
                      tenantUsers={tenantUsers}
                      onMembershipChanged={load}
                    />
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
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
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to remove member');
    } finally {
      setRemovingUserId(null);
    }
  };

  return (
    <div className="space-y-3">
      <div className="flex flex-col sm:flex-row gap-2">
        <select
          value={selectedUserId}
          onChange={e => setSelectedUserId(e.target.value)}
          className="flex-1 text-sm border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">
            {addableUsers.length === 0 ? 'No more users to add' : 'Select a user to add…'}
          </option>
          {addableUsers.map(u => (
            <option key={u.userId} value={u.userId}>{u.username} ({u.email})</option>
          ))}
        </select>
        <button
          onClick={handleAdd}
          disabled={!selectedUserId || adding}
          className="flex items-center justify-center gap-1.5 px-3 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 disabled:opacity-50 whitespace-nowrap"
        >
          <UserPlus size={14} /> Add
        </button>
      </div>

      {error && <div className="flex items-center gap-2 text-xs text-red-600 bg-red-50 rounded-lg px-3 py-2"><AlertCircle size={13} className="flex-shrink-0" />{error}</div>}

      {members === null ? (
        <div className="text-xs text-gray-400 py-2">Loading members…</div>
      ) : members.length === 0 ? (
        <p className="text-xs text-gray-400 italic py-1">No members yet.</p>
      ) : (
        <div className="space-y-1.5">
          {members.map(m => (
            <div key={m.userId} className="flex items-center justify-between bg-white border border-gray-200 rounded-lg px-3 py-2">
              <div>
                <span className="text-sm text-gray-800">{m.username}</span>
                <span className="text-xs text-gray-400 ml-2">{m.email}</span>
              </div>
              <button
                onClick={() => handleRemove(m.userId)}
                disabled={removingUserId === m.userId}
                className="p-1 text-red-400 hover:text-red-600 disabled:opacity-50 transition-colors"
                title="Remove from group"
              >
                <Trash2 size={14} />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
