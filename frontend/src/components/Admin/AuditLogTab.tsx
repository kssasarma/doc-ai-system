import React, { useEffect, useState, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { fetchAuditLog, AuditLogEntry } from '../../services/auditLogService';
import { Shield, ChevronLeft, ChevronRight } from 'lucide-react';

const ACTION_COLORS: Record<string, string> = {
  PRODUCT_ACCESS_GRANT: 'bg-green-100 text-green-700',
  PRODUCT_ACCESS_REVOKE: 'bg-red-100 text-red-700',
  ESCALATION_ANSWER: 'bg-orange-100 text-orange-700',
  DOCUMENT_UPLOAD: 'bg-blue-100 text-blue-700',
  DOCUMENT_DELETE: 'bg-red-100 text-red-600',
  USER_ROLE_CHANGE: 'bg-purple-100 text-purple-700',
  SHARE_LINK_CREATE: 'bg-teal-100 text-teal-700',
  SHARE_LINK_REVOKE: 'bg-gray-100 text-gray-600',
  COLLECTION_CREATE: 'bg-indigo-100 text-indigo-700',
};

export default function AuditLogTab() {
  const { token } = useAuth();
  const [entries, setEntries] = useState<AuditLogEntry[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [actionFilter, setActionFilter] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(async (pg: number, action: string) => {
    if (!token) return;
    setLoading(true);
    const res = await fetchAuditLog(token, pg, 50, action || undefined);
    if (res.success && res.data) {
      setEntries(res.data.content);
      setTotalPages(res.data.totalPages);
      setTotalElements(res.data.totalElements);
    }
    setLoading(false);
  }, [token]);

  useEffect(() => { load(page, actionFilter); }, [load, page, actionFilter]);

  return (
    <div className="space-y-4">
      {/* Filters */}
      <div className="bg-white rounded-xl border border-gray-200 p-4 flex items-center gap-3">
        <Shield size={16} className="text-gray-400" />
        <span className="text-sm font-medium text-gray-600">Filter by action:</span>
        <select
          value={actionFilter}
          onChange={e => { setActionFilter(e.target.value); setPage(0); }}
          className="text-sm border border-gray-200 rounded-lg px-2 py-1 focus:outline-none focus:ring-2 focus:ring-blue-400"
        >
          <option value="">All actions</option>
          <option value="PRODUCT_ACCESS_GRANT">Access Grant</option>
          <option value="PRODUCT_ACCESS_REVOKE">Access Revoke</option>
          <option value="ESCALATION_ANSWER">Escalation Answer</option>
          <option value="DOCUMENT_UPLOAD">Document Upload</option>
          <option value="DOCUMENT_DELETE">Document Delete</option>
          <option value="USER_ROLE_CHANGE">Role Change</option>
        </select>
        <span className="text-xs text-gray-400 ml-auto">{totalElements.toLocaleString()} total entries</span>
      </div>

      {/* Log table */}
      <div className="bg-white rounded-xl border border-gray-200">
        {loading ? (
          <div className="p-12 text-center text-gray-400">Loading audit log…</div>
        ) : entries.length === 0 ? (
          <div className="p-12 text-center text-gray-400 text-sm">No audit log entries yet.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wide">
                <tr>
                  <th className="px-4 py-3 text-left">Timestamp</th>
                  <th className="px-4 py-3 text-left">Actor</th>
                  <th className="px-4 py-3 text-left">Action</th>
                  <th className="px-4 py-3 text-left">Target</th>
                  <th className="px-4 py-3 text-left">Details</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {entries.map(e => (
                  <tr key={e.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">
                      {new Date(e.createdAt).toLocaleString()}
                    </td>
                    <td className="px-4 py-3 text-gray-800 font-medium">
                      {e.actorUsername ?? 'system'}
                    </td>
                    <td className="px-4 py-3">
                      <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${ACTION_COLORS[e.action] ?? 'bg-gray-100 text-gray-600'}`}>
                        {e.action}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500">
                      {e.targetType ?? '—'}
                      {e.targetId && <div className="font-mono text-[10px] text-gray-400 truncate max-w-24" title={e.targetId}>{e.targetId.slice(0, 8)}…</div>}
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500 max-w-xs truncate" title={e.metadata ?? ''}>
                      {e.metadata ?? '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="px-4 py-3 border-t border-gray-100 flex items-center justify-between">
            <span className="text-xs text-gray-500">Page {page + 1} of {totalPages}</span>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="p-1 text-gray-400 hover:text-gray-700 disabled:opacity-30 transition-colors"
              >
                <ChevronLeft size={16} />
              </button>
              <button
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="p-1 text-gray-400 hover:text-gray-700 disabled:opacity-30 transition-colors"
              >
                <ChevronRight size={16} />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
