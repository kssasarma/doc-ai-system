import React, { useCallback, useEffect, useState } from 'react';
import { Users, UserPlus, Shield, AlertCircle, CheckCircle, RefreshCw } from 'lucide-react';
import { getTenantUsers } from '../../services/tenantService';
import { inviteUser } from '../../services/invitationService';
import type { TenantUser } from '../../types';
import { useAuth } from '../../context/AuthContext';

export default function UsersPage() {
  const { token, user } = useAuth();
  const tenantId = user?.tenantId ?? '';

  const [users, setUsers] = useState<TenantUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [inviteEmail, setInviteEmail] = useState('');
  const [inviting, setInviting] = useState(false);
  const [inviteMsg, setInviteMsg] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

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
    setInviteMsg(null);
    try {
      await inviteUser(token, inviteEmail);
      setInviteMsg({ type: 'success', text: `Invitation sent to ${inviteEmail}.` });
      setInviteEmail('');
    } catch (e) {
      const detail = (e as { response?: { data?: { error?: string } } })?.response?.data?.error;
      setInviteMsg({ type: 'error', text: detail || 'Failed to send invitation.' });
    } finally {
      setInviting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-gray-800 mb-1">Users</h2>
        <p className="text-sm text-gray-500">Invite users into your tenant. Grant per-document access from the Documents page.</p>
      </div>

      {/* Invite form */}
      <div className="bg-white rounded-xl border border-gray-200 p-5">
        <h3 className="text-sm font-semibold text-gray-700 mb-1 flex items-center gap-2">
          <UserPlus size={16} className="text-blue-500" /> Invite a user
        </h3>
        <p className="text-xs text-gray-400 mb-4">Sends an email invitation to join your tenant as a regular user.</p>
        <form onSubmit={handleInvite} className="flex flex-col sm:flex-row gap-3">
          <input
            type="email"
            value={inviteEmail}
            onChange={e => setInviteEmail(e.target.value)}
            required
            placeholder="user@company.com"
            className="flex-1 text-sm border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <button
            type="submit"
            disabled={inviting}
            className="flex items-center justify-center gap-1.5 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-60 transition-colors"
          >
            {inviting ? <RefreshCw size={14} className="animate-spin" /> : <UserPlus size={14} />}
            {inviting ? 'Sending…' : 'Send invite'}
          </button>
        </form>
        {inviteMsg && (
          <div className={`flex items-center gap-2 text-xs mt-3 rounded-lg px-3 py-2 ${inviteMsg.type === 'success' ? 'text-green-700 bg-green-50' : 'text-red-600 bg-red-50'}`}>
            {inviteMsg.type === 'success' ? <CheckCircle size={14} className="flex-shrink-0" /> : <AlertCircle size={14} className="flex-shrink-0" />}
            {inviteMsg.text}
          </div>
        )}
      </div>

      {/* User list */}
      <div className="bg-white rounded-xl border border-gray-200">
        <div className="px-5 py-4 border-b border-gray-100 flex items-center gap-2">
          <Users size={16} className="text-gray-400" />
          <h3 className="text-sm font-semibold text-gray-700">Tenant Users</h3>
          {!loading && <span className="ml-auto text-xs text-gray-400">{users.length} users</span>}
        </div>
        {loading ? (
          <div className="p-12 text-center text-gray-400">Loading users…</div>
        ) : error ? (
          <div className="p-6 text-center text-red-500 flex items-center justify-center gap-2"><AlertCircle className="w-5 h-5" />{error}</div>
        ) : users.length === 0 ? (
          <div className="p-12 text-center text-gray-400">No users yet. Invite your first user above.</div>
        ) : (
          <div className="divide-y divide-gray-100">
            {users.map(u => (
              <div key={u.userId} className="flex items-center gap-3 px-5 py-3">
                <div className="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center text-white text-sm font-bold flex-shrink-0">
                  {u.username.charAt(0).toUpperCase()}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-gray-900">{u.username}</span>
                    <span className={`px-1.5 py-0.5 text-[10px] rounded font-medium ${u.role === 'ADMIN' ? 'bg-purple-100 text-purple-700' : 'bg-gray-100 text-gray-500'}`}>
                      {u.role}
                    </span>
                    {u.role === 'ADMIN' && <Shield size={12} className="text-purple-500" />}
                  </div>
                  <span className="text-xs text-gray-400 truncate">{u.email}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
