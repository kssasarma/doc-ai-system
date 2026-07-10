import React, { useCallback, useEffect, useState } from 'react';
import { UserPlus, Trash2, AlertCircle, Lock } from 'lucide-react';
import { listGrantees, grantDocumentAccess, revokeDocumentAccess } from '../../services/documentAccessService';
import type { DocumentGrantee, TenantUser } from '../../types';

export default function DocumentAccessManager({
  token, documentId, tenantUsers,
}: {
  token: string;
  documentId: string;
  tenantUsers: TenantUser[];
}) {
  const [grantees, setGrantees] = useState<DocumentGrantee[] | null>(null);
  const [error, setError] = useState('');
  const [selectedUserId, setSelectedUserId] = useState('');
  const [granting, setGranting] = useState(false);
  const [revokingUserId, setRevokingUserId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError('');
    try {
      setGrantees(await listGrantees(token, documentId));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load access grants');
    }
  }, [token, documentId]);

  useEffect(() => { load(); }, [load]);

  const grantedIds = new Set((grantees ?? []).map(g => g.userId));
  const grantableUsers = tenantUsers.filter(u => u.role === 'USER' && !grantedIds.has(u.userId));

  const handleGrant = async () => {
    if (!selectedUserId) return;
    setGranting(true);
    setError('');
    try {
      await grantDocumentAccess(token, documentId, selectedUserId);
      setSelectedUserId('');
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to grant access');
    } finally {
      setGranting(false);
    }
  };

  const handleRevoke = async (userId: string) => {
    setRevokingUserId(userId);
    try {
      await revokeDocumentAccess(token, documentId, userId);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to revoke access');
    } finally {
      setRevokingUserId(null);
    }
  };

  return (
    <div className="space-y-3">
      <div className="flex items-start gap-2 text-xs text-gray-400">
        <Lock size={13} className="flex-shrink-0 mt-0.5" />
        <span>Only users granted access below (plus any tenant admin) can see this document in chat.</span>
      </div>

      <div className="flex flex-col sm:flex-row gap-2">
        <select
          value={selectedUserId}
          onChange={e => setSelectedUserId(e.target.value)}
          className="flex-1 text-sm border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">
            {grantableUsers.length === 0 ? 'No more users to grant' : 'Select a user to grant access…'}
          </option>
          {grantableUsers.map(u => (
            <option key={u.userId} value={u.userId}>{u.username} ({u.email})</option>
          ))}
        </select>
        <button
          onClick={handleGrant}
          disabled={!selectedUserId || granting}
          className="flex items-center justify-center gap-1.5 px-3 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 disabled:opacity-50 whitespace-nowrap"
        >
          <UserPlus size={14} /> Grant
        </button>
      </div>

      {error && <div className="flex items-center gap-2 text-xs text-red-600 bg-red-50 rounded-lg px-3 py-2"><AlertCircle size={13} className="flex-shrink-0" />{error}</div>}

      {grantees === null ? (
        <div className="text-xs text-gray-400 py-2">Loading grants…</div>
      ) : grantees.length === 0 ? (
        <p className="text-xs text-gray-400 italic py-1">No users have been granted access yet.</p>
      ) : (
        <div className="space-y-1.5">
          {grantees.map(g => (
            <div key={g.userId} className="flex items-center justify-between bg-gray-50 border border-gray-200 rounded-lg px-3 py-2">
              <span className="text-sm text-gray-800">{g.username}</span>
              <button
                onClick={() => handleRevoke(g.userId)}
                disabled={revokingUserId === g.userId}
                className="p-1 text-red-400 hover:text-red-600 disabled:opacity-50 transition-colors"
                title="Revoke access"
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
