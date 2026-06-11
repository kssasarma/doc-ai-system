import React, { useEffect, useState } from 'react';
import { Shield, Trash2, Download, RefreshCw } from 'lucide-react';
import axios from 'axios';
import { useAuth } from '../../context/AuthContext';

const BOT_URL = import.meta.env.VITE_BOT_API_URL ?? 'http://localhost:8082';

interface DeletionRequest {
  id: string;
  userId: string;
  requestedAt: string;
  status: string;
}

export default function GdprTab() {
  const { token } = useAuth();
  const [requests, setRequests] = useState<DeletionRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [processingId, setProcessingId] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);

  useEffect(() => { load(); }, []);

  const load = async () => {
    if (!token) return;
    setLoading(true);
    try {
      const { data } = await axios.get<DeletionRequest[]>(
        `${BOT_URL}/api/user/gdpr/admin/deletion-requests`,
        { headers: { Authorization: `Bearer ${token}` } },
      );
      setRequests(data);
    } catch { /* ignore */ }
    setLoading(false);
  };

  const handleProcess = async (id: string, userId: string) => {
    if (!token) return;
    setProcessingId(id);
    setMsg(null);
    try {
      await axios.delete(`${BOT_URL}/api/admin/users/${userId}`,
        { headers: { Authorization: `Bearer ${token}` } });
      setRequests(prev => prev.filter(r => r.id !== id));
      setMsg('User data deleted successfully.');
    } catch {
      setMsg('Failed to process deletion request.');
    }
    setProcessingId(null);
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-gray-800 mb-1">GDPR & Compliance</h2>
        <p className="text-sm text-gray-500">
          Manage data subject requests. Pending deletion requests must be processed within 30 days (GDPR Article 17).
        </p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <ComplianceCard icon={<Shield size={18} className="text-green-600" />} title="Data Portability"
          description="Users can export all their data via Account Settings → Export My Data." color="green" />
        <ComplianceCard icon={<Trash2 size={18} className="text-red-600" />} title="Right to Erasure"
          description="Deletion requests submitted by users appear below for admin processing." color="red" />
        <ComplianceCard icon={<Download size={18} className="text-blue-600" />} title="Retention Policies"
          description="Configure auto-deletion windows per tenant in the Tenant Management tab." color="blue" />
      </div>

      <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
        <div className="px-4 py-3 border-b border-gray-100 flex items-center justify-between">
          <h3 className="text-sm font-medium text-gray-700">Pending Deletion Requests</h3>
          <button onClick={load} className="p-1.5 text-gray-400 hover:text-gray-600">
            <RefreshCw size={14} />
          </button>
        </div>

        {loading && (
          <div className="flex justify-center py-8">
            <div className="animate-spin h-6 w-6 border-b-2 border-red-500 rounded-full" />
          </div>
        )}

        {!loading && requests.length === 0 && (
          <div className="text-center py-10 text-gray-400">
            <Shield size={32} className="mx-auto mb-2 opacity-30" />
            <p className="text-sm font-medium">No pending deletion requests</p>
          </div>
        )}

        {requests.map(req => (
          <div key={req.id} className="flex items-center gap-3 px-4 py-3 border-b border-gray-50 last:border-0">
            <div className="flex-1 min-w-0">
              <p className="text-sm text-gray-800 font-mono truncate">{req.userId}</p>
              <p className="text-xs text-gray-400 mt-0.5">
                Requested {new Date(req.requestedAt).toLocaleDateString()}
              </p>
            </div>
            <span className={`text-xs px-2 py-0.5 rounded-full ${
              req.status === 'PENDING' ? 'bg-yellow-50 text-yellow-600' :
              req.status === 'COMPLETED' ? 'bg-green-50 text-green-600' : 'bg-red-50 text-red-600'
            }`}>{req.status}</span>
            {req.status === 'PENDING' && (
              <button
                onClick={() => handleProcess(req.id, req.userId)}
                disabled={processingId === req.id}
                className="flex items-center gap-1 px-3 py-1.5 text-xs bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50"
              >
                {processingId === req.id ? <RefreshCw size={12} className="animate-spin" /> : <Trash2 size={12} />}
                Erase
              </button>
            )}
          </div>
        ))}
      </div>

      {msg && (
        <p className={`text-sm px-3 py-2 rounded-lg ${msg.includes('Failed') ? 'bg-red-50 text-red-600' : 'bg-green-50 text-green-700'}`}>
          {msg}
        </p>
      )}
    </div>
  );
}

function ComplianceCard({ icon, title, description, color }: {
  icon: React.ReactNode; title: string; description: string;
  color: 'green' | 'red' | 'blue';
}) {
  const bg = { green: 'bg-green-50 border-green-100', red: 'bg-red-50 border-red-100', blue: 'bg-blue-50 border-blue-100' }[color];
  return (
    <div className={`border rounded-xl p-4 ${bg}`}>
      <div className="flex items-center gap-2 mb-2">{icon}<span className="text-sm font-medium text-gray-800">{title}</span></div>
      <p className="text-xs text-gray-500">{description}</p>
    </div>
  );
}
