import React, { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Key, Plus, Trash2, Copy, Check, AlertTriangle, Clock, Shield } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { fetchApiKeys, createApiKey, revokeApiKey, ApiKey } from '../../services/apiKeyService';

const SCOPE_OPTIONS = ['query', 'upload', 'admin'];

function ScopeBadge({ scope }: { scope: string }) {
  const colors: Record<string, string> = {
    query: 'bg-blue-100 text-blue-700',
    upload: 'bg-purple-100 text-purple-700',
    admin: 'bg-red-100 text-red-700',
  };
  return (
    <span className={`px-1.5 py-0.5 rounded text-[10px] font-medium ${colors[scope] ?? 'bg-gray-100 text-gray-600'}`}>
      {scope}
    </span>
  );
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <button onClick={copy} className="p-1 text-gray-400 hover:text-gray-700 transition-colors" title="Copy">
      {copied ? <Check size={14} className="text-green-500" /> : <Copy size={14} />}
    </button>
  );
}

export default function ApiKeysPage() {
  const navigate = useNavigate();
  const { token } = useAuth();
  const [keys, setKeys] = useState<ApiKey[]>([]);
  const [loading, setLoading] = useState(true);
  const [newKeySecret, setNewKeySecret] = useState<string | null>(null);

  // Create form
  const [name, setName] = useState('');
  const [scopes, setScopes] = useState<string[]>(['query']);
  const [rateLimit, setRateLimit] = useState(60);
  const [expirationDays, setExpirationDays] = useState<number | ''>('');
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState('');

  const load = useCallback(async () => {
    if (!token) return;
    const res = await fetchApiKeys(token);
    if (res.success && res.data) setKeys(res.data);
    setLoading(false);
  }, [token]);

  useEffect(() => { load(); }, [load]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || !name.trim()) return;
    setCreating(true);
    setCreateError('');
    setNewKeySecret(null);

    const res = await createApiKey(token, {
      name: name.trim(),
      scopes,
      rateLimitPerMin: rateLimit,
      expirationDays: expirationDays !== '' ? Number(expirationDays) : undefined,
    });

    if (res.success && res.data) {
      setNewKeySecret(res.data.rawKey);
      setName('');
      setScopes(['query']);
      setRateLimit(60);
      setExpirationDays('');
      await load();
    } else {
      setCreateError(res.error ?? 'Failed to create key');
    }
    setCreating(false);
  };

  const handleRevoke = async (key: ApiKey) => {
    if (!token || !window.confirm(`Revoke key "${key.name}"? This cannot be undone.`)) return;
    const res = await revokeApiKey(token, key.id);
    if (res.success) await load();
  };

  const toggleScope = (scope: string) => {
    setScopes(prev =>
      prev.includes(scope) ? prev.filter(s => s !== scope) : [...prev, scope]
    );
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="bg-white border-b border-gray-200 px-6 py-4 flex items-center gap-4">
        <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-gray-600 hover:text-gray-900 text-sm transition-colors">
          <ArrowLeft className="w-4 h-4" />
          Back
        </button>
        <div className="flex items-center gap-2">
          <Key className="w-5 h-5 text-blue-600" />
          <h1 className="text-xl font-semibold text-gray-900">API Keys</h1>
        </div>
      </div>

      <div className="max-w-4xl mx-auto px-6 py-8 space-y-8">

        {/* Intro */}
        <div className="bg-blue-50 border border-blue-100 rounded-xl p-5">
          <h2 className="text-sm font-semibold text-blue-800 mb-1">Programmatic Access</h2>
          <p className="text-sm text-blue-700">
            API keys let you query Docs-inator from scripts, CI/CD pipelines, Slack bots, and IDE extensions.
            Authenticate by setting <code className="bg-blue-100 px-1 rounded text-xs">X-API-Key: sk-docai-…</code> or{' '}
            <code className="bg-blue-100 px-1 rounded text-xs">Authorization: ApiKey sk-docai-…</code>
          </p>
          <div className="mt-3 bg-white rounded-lg p-3 font-mono text-xs text-gray-700 border border-blue-100">
            <div className="text-gray-400 mb-1"># Example: query the API</div>
            <div>curl -X POST https://your-domain.com/api/v1/query \</div>
            <div className="pl-4">-H "X-API-Key: sk-docai-xxxxxxxxxxxx" \</div>
            <div className="pl-4">-H "Content-Type: application/json" \</div>
            <div className="pl-4">-d {`'{"question":"How do I configure LDAP?","product":"case360","version":"23.4"}'`}</div>
          </div>
        </div>

        {/* New key revealed */}
        {newKeySecret && (
          <div className="bg-green-50 border border-green-200 rounded-xl p-5">
            <div className="flex items-start gap-3">
              <AlertTriangle className="w-5 h-5 text-green-700 flex-shrink-0 mt-0.5" />
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-green-800">Key created — save it now</p>
                <p className="text-xs text-green-700 mt-0.5 mb-3">This is the only time you will see this key. It cannot be retrieved later.</p>
                <div className="flex items-center gap-2 bg-white rounded-lg px-3 py-2 border border-green-200 font-mono text-sm break-all">
                  <span className="flex-1 text-gray-900">{newKeySecret}</span>
                  <CopyButton text={newKeySecret} />
                </div>
              </div>
            </div>
            <button onClick={() => setNewKeySecret(null)} className="mt-3 text-xs text-green-700 underline hover:text-green-900">
              I've saved it, dismiss
            </button>
          </div>
        )}

        {/* Create form */}
        <div className="bg-white rounded-xl border border-gray-200 p-6">
          <h3 className="text-sm font-semibold text-gray-700 mb-4 flex items-center gap-2">
            <Plus size={16} className="text-blue-500" />
            Create New Key
          </h3>
          <form onSubmit={handleCreate} className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Key Name *</label>
                <input
                  type="text"
                  value={name}
                  onChange={e => setName(e.target.value)}
                  required
                  placeholder="e.g. Slack Bot, CI Pipeline"
                  className="w-full text-sm border border-gray-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-400"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Rate Limit (req/min)</label>
                <input
                  type="number"
                  value={rateLimit}
                  onChange={e => setRateLimit(Number(e.target.value))}
                  min={1}
                  max={600}
                  className="w-full text-sm border border-gray-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-400"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-2">Scopes</label>
                <div className="flex flex-wrap gap-2">
                  {SCOPE_OPTIONS.map(scope => (
                    <label key={scope} className="flex items-center gap-1.5 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={scopes.includes(scope)}
                        onChange={() => toggleScope(scope)}
                        className="rounded"
                      />
                      <ScopeBadge scope={scope} />
                    </label>
                  ))}
                </div>
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Expiration (days, optional)</label>
                <input
                  type="number"
                  value={expirationDays}
                  onChange={e => setExpirationDays(e.target.value === '' ? '' : Number(e.target.value))}
                  min={1}
                  placeholder="Never"
                  className="w-full text-sm border border-gray-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-400"
                />
              </div>
            </div>

            {createError && (
              <p className="text-xs text-red-600 bg-red-50 rounded-lg px-3 py-2">{createError}</p>
            )}
            <button
              type="submit"
              disabled={creating || !name.trim() || scopes.length === 0}
              className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
            >
              <Key size={14} />
              {creating ? 'Creating…' : 'Create Key'}
            </button>
          </form>
        </div>

        {/* Keys list */}
        <div className="bg-white rounded-xl border border-gray-200">
          <div className="px-5 py-4 border-b border-gray-100 flex items-center gap-2">
            <Shield size={16} className="text-gray-400" />
            <h3 className="text-sm font-semibold text-gray-700">Your API Keys</h3>
            <span className="ml-auto text-xs text-gray-400">{keys.length} key{keys.length !== 1 ? 's' : ''}</span>
          </div>

          {loading ? (
            <div className="p-12 text-center text-gray-400">Loading…</div>
          ) : keys.length === 0 ? (
            <div className="p-12 text-center text-gray-400 text-sm">No API keys yet. Create one above.</div>
          ) : (
            <div className="divide-y divide-gray-100">
              {keys.map(key => (
                <div key={key.id} className={`px-5 py-4 flex items-start gap-4 ${key.revoked ? 'opacity-50' : ''}`}>
                  <Key size={16} className="text-gray-400 mt-0.5 flex-shrink-0" />
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-medium text-gray-900">{key.name}</span>
                      {key.revoked && (
                        <span className="px-1.5 py-0.5 rounded text-[10px] font-medium bg-red-100 text-red-600">Revoked</span>
                      )}
                      {key.scopes.map(s => <ScopeBadge key={s} scope={s} />)}
                    </div>
                    <div className="flex items-center gap-3 mt-1 flex-wrap">
                      <code className="text-xs text-gray-500 bg-gray-50 px-2 py-0.5 rounded font-mono">
                        {key.keyPrefix}…
                      </code>
                      <span className="text-xs text-gray-400">{key.rateLimitPerMin} req/min</span>
                      {key.lastUsedAt && (
                        <span className="flex items-center gap-1 text-xs text-gray-400">
                          <Clock size={10} /> Last used {new Date(key.lastUsedAt).toLocaleDateString()}
                        </span>
                      )}
                      {key.expiresAt && (
                        <span className="flex items-center gap-1 text-xs text-gray-400">
                          <Clock size={10} /> Expires {new Date(key.expiresAt).toLocaleDateString()}
                        </span>
                      )}
                    </div>
                    <div className="text-xs text-gray-400 mt-0.5">
                      Created {new Date(key.createdAt).toLocaleDateString()}
                    </div>
                  </div>
                  {!key.revoked && (
                    <button
                      onClick={() => handleRevoke(key)}
                      className="p-1.5 text-red-400 hover:text-red-600 transition-colors flex-shrink-0"
                      title="Revoke key"
                    >
                      <Trash2 size={15} />
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
