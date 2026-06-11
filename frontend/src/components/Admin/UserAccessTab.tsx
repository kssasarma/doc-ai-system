import React, { useEffect, useState, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { fetchAllUsersWithAccess, grantAccess, revokeAccess, UserWithAccess } from '../../services/productAccessService';
import { Users, Plus, Trash2, Shield, ChevronDown, ChevronRight } from 'lucide-react';

export default function UserAccessTab() {
  const { token } = useAuth();
  const [users, setUsers] = useState<UserWithAccess[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedUser, setExpandedUser] = useState<string | null>(null);

  // Grant form state
  const [grantUserId, setGrantUserId] = useState('');
  const [grantProduct, setGrantProduct] = useState('');
  const [grantVersion, setGrantVersion] = useState('');
  const [granting, setGranting] = useState(false);
  const [grantError, setGrantError] = useState('');

  const load = useCallback(async () => {
    if (!token) return;
    const res = await fetchAllUsersWithAccess(token);
    if (res.success && res.data) setUsers(res.data);
    setLoading(false);
  }, [token]);

  useEffect(() => { load(); }, [load]);

  const handleGrant = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || !grantUserId || !grantProduct) return;
    setGranting(true);
    setGrantError('');
    const res = await grantAccess(token, grantUserId, grantProduct, grantVersion || undefined);
    if (res.success) {
      setGrantProduct('');
      setGrantVersion('');
      setGrantUserId('');
      await load();
    } else {
      setGrantError(res.error ?? 'Failed to grant access');
    }
    setGranting(false);
  };

  const handleRevoke = async (grantId: string) => {
    if (!token) return;
    const res = await revokeAccess(token, grantId);
    if (res.success) await load();
  };

  if (loading) return <div className="p-12 text-center text-gray-400">Loading users…</div>;

  const regularUsers = users.filter(u => u.role === 'USER');

  return (
    <div className="space-y-6">
      {/* Grant form */}
      <div className="bg-white rounded-xl border border-gray-200 p-5">
        <h3 className="text-sm font-semibold text-gray-700 mb-1 flex items-center gap-2">
          <Plus size={16} className="text-blue-500" />
          Grant Product Access
        </h3>
        <p className="text-xs text-gray-400 mb-4">Grant a user access to a specific product (and optionally a specific version). Leave version blank for all-versions access.</p>
        <form onSubmit={handleGrant} className="grid grid-cols-1 sm:grid-cols-4 gap-3">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">User *</label>
            <select
              value={grantUserId}
              onChange={e => setGrantUserId(e.target.value)}
              required
              className="w-full text-sm border border-gray-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-400"
            >
              <option value="">Select user…</option>
              {regularUsers.map(u => (
                <option key={u.userId} value={u.userId}>{u.username} ({u.email})</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Product *</label>
            <input
              type="text"
              value={grantProduct}
              onChange={e => setGrantProduct(e.target.value)}
              required
              placeholder="e.g. case360"
              className="w-full text-sm border border-gray-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-400"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Version (optional)</label>
            <input
              type="text"
              value={grantVersion}
              onChange={e => setGrantVersion(e.target.value)}
              placeholder="e.g. 23.4 or blank=all"
              className="w-full text-sm border border-gray-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-400"
            />
          </div>
          <div className="flex items-end">
            <button
              type="submit"
              disabled={granting}
              className="w-full flex items-center justify-center gap-1.5 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-60 transition-colors"
            >
              <Plus size={14} />
              {granting ? 'Granting…' : 'Grant Access'}
            </button>
          </div>
        </form>
        {grantError && <p className="text-xs text-red-600 mt-2">{grantError}</p>}
      </div>

      {/* User list */}
      <div className="bg-white rounded-xl border border-gray-200">
        <div className="px-5 py-4 border-b border-gray-100 flex items-center gap-2">
          <Users size={16} className="text-gray-400" />
          <h3 className="text-sm font-semibold text-gray-700">All Users & Product Access</h3>
          <span className="ml-auto text-xs text-gray-400">{users.length} users</span>
        </div>
        <div className="divide-y divide-gray-100">
          {users.map(u => (
            <div key={u.userId}>
              <button
                onClick={() => setExpandedUser(expandedUser === u.userId ? null : u.userId)}
                className="w-full flex items-center gap-3 px-5 py-3 hover:bg-gray-50 transition-colors text-left"
              >
                <div className="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center text-white text-sm font-bold flex-shrink-0">
                  {u.username.charAt(0).toUpperCase()}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-gray-900">{u.username}</span>
                    <span className={`px-1.5 py-0.5 text-[10px] rounded font-medium ${u.role === 'ADMIN' ? 'bg-purple-100 text-purple-700' : 'bg-gray-100 text-gray-500'}`}>
                      {u.role}
                    </span>
                    {u.role === 'ADMIN' && (
                      <Shield size={12} className="text-purple-500" />
                    )}
                  </div>
                  <span className="text-xs text-gray-400">{u.email}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-xs text-gray-400">{u.grants.length} grant{u.grants.length !== 1 ? 's' : ''}</span>
                  {expandedUser === u.userId ? <ChevronDown size={14} className="text-gray-400" /> : <ChevronRight size={14} className="text-gray-400" />}
                </div>
              </button>

              {expandedUser === u.userId && (
                <div className="bg-gray-50 px-5 py-3 border-t border-gray-100">
                  {u.role === 'ADMIN' ? (
                    <p className="text-xs text-purple-600 italic">Admins have unrestricted access to all products and versions.</p>
                  ) : u.grants.length === 0 ? (
                    <p className="text-xs text-gray-400 italic">No specific product access granted. User can query all public products.</p>
                  ) : (
                    <div className="space-y-1.5">
                      {u.grants.map(g => (
                        <div key={g.id} className="flex items-center justify-between bg-white border border-gray-200 rounded-lg px-3 py-2">
                          <div>
                            <span className="text-sm font-medium text-gray-800">{g.product}</span>
                            <span className="text-xs text-gray-400 ml-2">{g.version ? `v${g.version}` : 'all versions'}</span>
                          </div>
                          <button
                            onClick={() => handleRevoke(g.id)}
                            className="p-1 text-red-400 hover:text-red-600 transition-colors"
                            title="Revoke access"
                          >
                            <Trash2 size={14} />
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
