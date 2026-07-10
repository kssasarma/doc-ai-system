import React, { useEffect, useState } from 'react';
import { Building2, Plus, Save, RefreshCw, ChevronDown, ChevronUp, AlertCircle, CheckCircle } from 'lucide-react';
import {
  listTenants, createTenant, updateTenant,
  getTenantLLMConfig, updateTenantLLMConfig,
  getRetentionPolicy, updateRetentionPolicy,
  type Tenant, type TenantLLMConfig, type DataRetentionPolicy,
} from '../../services/tenantService';
import { useAuth } from '../../context/AuthContext';

const inputCls = 'w-full text-sm border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500';

const EMPTY_FORM = { name: '', slug: '', plan: 'FREE', maxUsers: 10, maxDocuments: 100, adminEmail: '' };

export default function TenantsPage() {
  const { token } = useAuth();
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [activePanel, setActivePanel] = useState<Record<string, 'info' | 'llm' | 'retention'>>({});
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [msg, setMsg] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  useEffect(() => { load(); }, []);

  const load = async () => {
    if (!token) return;
    setLoading(true);
    setLoadError('');
    try {
      const data = await listTenants(token);
      setTenants(data);
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : 'Failed to load tenants');
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || !form.name || !form.slug || !form.adminEmail) return;
    setCreating(true);
    setMsg(null);
    try {
      const t = await createTenant(token, form);
      setTenants(prev => [...prev, t]);
      setForm(EMPTY_FORM);
      setMsg({ type: 'success', text: `Tenant "${t.name}" created. An invitation was emailed to ${form.adminEmail}.` });
    } catch (e) {
      const detail = (e as { response?: { data?: { error?: string } } })?.response?.data?.error;
      setMsg({ type: 'error', text: detail || 'Failed to create tenant.' });
    } finally {
      setCreating(false);
    }
  };

  const toggle = (id: string) => {
    setExpandedId(prev => prev === id ? null : id);
    setActivePanel(prev => ({ ...prev, [id]: prev[id] ?? 'info' }));
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-gray-800 mb-1">Tenant Management</h2>
        <p className="text-sm text-gray-500">Create tenants and manage isolation, LLM provider config, and data retention.</p>
      </div>

      {/* Create tenant */}
      <div className="bg-blue-50 border border-blue-100 rounded-xl p-4">
        <div className="flex items-center gap-2 mb-3 text-sm font-medium text-blue-800">
          <Plus size={14} /> Create new tenant
        </div>
        <form onSubmit={handleCreate} className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Name *</label>
            <input className={inputCls} placeholder="Acme Corp" value={form.name}
              onChange={e => setForm(f => ({ ...f, name: e.target.value }))} required />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Slug *</label>
            <input className={inputCls} placeholder="acme-corp" value={form.slug}
              onChange={e => setForm(f => ({ ...f, slug: e.target.value }))} required />
          </div>
          <div className="sm:col-span-2">
            <label className="block text-xs font-medium text-gray-600 mb-1">Admin email *</label>
            <input type="email" className={inputCls} placeholder="admin@acme.com" value={form.adminEmail}
              onChange={e => setForm(f => ({ ...f, adminEmail: e.target.value }))} required />
            <p className="text-xs text-gray-400 mt-1">This person is invited as the tenant's first admin.</p>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Plan</label>
            <select className={inputCls} value={form.plan} onChange={e => setForm(f => ({ ...f, plan: e.target.value }))}>
              <option>FREE</option><option>PRO</option><option>ENTERPRISE</option>
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Max users</label>
            <input type="number" min={1} className={inputCls} value={form.maxUsers}
              onChange={e => setForm(f => ({ ...f, maxUsers: +e.target.value }))} />
          </div>
          <button type="submit" disabled={creating}
            className="sm:col-span-2 flex justify-center items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 disabled:opacity-50">
            {creating ? <RefreshCw size={14} className="animate-spin" /> : <Plus size={14} />} Create & invite admin
          </button>
        </form>
        {msg && (
          <div className={`flex items-center gap-2 text-xs mt-3 rounded-lg px-3 py-2 ${msg.type === 'success' ? 'text-green-700 bg-green-50' : 'text-red-600 bg-red-50'}`}>
            {msg.type === 'success' ? <CheckCircle size={14} className="flex-shrink-0" /> : <AlertCircle size={14} className="flex-shrink-0" />}
            {msg.text}
          </div>
        )}
      </div>

      {loading ? (
        <div className="flex justify-center py-8"><div className="animate-spin h-7 w-7 border-b-2 border-blue-600 rounded-full" /></div>
      ) : loadError ? (
        <div className="p-6 text-center text-red-500 flex items-center justify-center gap-2"><AlertCircle className="w-5 h-5" />{loadError}</div>
      ) : tenants.length === 0 ? (
        <div className="p-12 text-center text-gray-400 bg-white border border-gray-200 rounded-xl">No tenants yet. Create the first one above.</div>
      ) : (
        <div className="space-y-3">
          {tenants.map(t => (
            <TenantCard key={t.id} tenant={t} token={token!}
              expanded={expandedId === t.id}
              panel={activePanel[t.id] ?? 'info'}
              onToggle={() => toggle(t.id)}
              onPanelChange={p => setActivePanel(prev => ({ ...prev, [t.id]: p }))}
              onUpdated={updated => setTenants(prev => prev.map(x => x.id === updated.id ? updated : x))}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function TenantCard({ tenant, token, expanded, panel, onToggle, onPanelChange, onUpdated }: {
  tenant: Tenant; token: string; expanded: boolean;
  panel: 'info' | 'llm' | 'retention';
  onToggle: () => void;
  onPanelChange: (p: 'info' | 'llm' | 'retention') => void;
  onUpdated: (t: Tenant) => void;
}) {
  const [llmConfig, setLlmConfig] = useState<TenantLLMConfig | null>(null);
  const [retention, setRetention] = useState<DataRetentionPolicy | null>(null);
  const [saving, setSaving] = useState(false);
  const [togglingActive, setTogglingActive] = useState(false);

  useEffect(() => {
    if (!expanded) return;
    if (panel === 'llm' && !llmConfig) {
      getTenantLLMConfig(token, tenant.id).then(setLlmConfig).catch(() => {});
    }
    if (panel === 'retention' && !retention) {
      getRetentionPolicy(token, tenant.id).then(setRetention).catch(() => {});
    }
  }, [expanded, panel]);

  const saveLLM = async () => {
    if (!llmConfig) return;
    setSaving(true);
    try {
      const updated = await updateTenantLLMConfig(token, tenant.id, llmConfig);
      setLlmConfig(updated);
    } finally {
      setSaving(false);
    }
  };

  const saveRetention = async () => {
    if (!retention) return;
    setSaving(true);
    try {
      const updated = await updateRetentionPolicy(token, tenant.id, retention);
      setRetention(updated);
    } finally {
      setSaving(false);
    }
  };

  const toggleActive = async () => {
    setTogglingActive(true);
    try {
      const updated = await updateTenant(token, tenant.id, {
        name: tenant.name, plan: tenant.plan, active: !tenant.active,
        maxUsers: tenant.maxUsers, maxDocuments: tenant.maxDocuments,
      });
      onUpdated(updated);
    } finally {
      setTogglingActive(false);
    }
  };

  const planBadge: Record<string, string> = {
    FREE: 'bg-gray-100 text-gray-600',
    PRO: 'bg-blue-50 text-blue-600',
    ENTERPRISE: 'bg-purple-50 text-purple-600',
  };

  return (
    <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <div className="flex items-center gap-3 p-4">
        <Building2 size={16} className="text-blue-500 flex-shrink-0" />
        <button onClick={onToggle} className="flex-1 text-left min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm font-medium text-gray-800">{tenant.name}</span>
            <span className="text-xs text-gray-400">/{tenant.slug}</span>
            <span className={`text-xs px-2 py-0.5 rounded-full ${planBadge[tenant.plan] ?? 'bg-gray-100 text-gray-500'}`}>
              {tenant.plan}
            </span>
            {!tenant.active && <span className="text-xs px-2 py-0.5 bg-red-50 text-red-500 rounded-full">Inactive</span>}
          </div>
          <div className="text-xs text-gray-400 mt-0.5">
            {tenant.maxUsers} users · {tenant.maxDocuments} docs
          </div>
        </button>
        <button onClick={toggleActive} disabled={togglingActive}
          className={`text-xs px-2.5 py-1.5 rounded-lg border transition-colors disabled:opacity-50 ${
            tenant.active ? 'border-red-200 text-red-600 hover:bg-red-50' : 'border-green-200 text-green-600 hover:bg-green-50'
          }`}>
          {tenant.active ? 'Deactivate' : 'Activate'}
        </button>
        <button onClick={onToggle} className="p-1.5 text-gray-400 hover:text-gray-600">
          {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
        </button>
      </div>

      {expanded && (
        <div className="border-t border-gray-100">
          <div className="flex border-b border-gray-100">
            {(['info', 'llm', 'retention'] as const).map(p => (
              <button key={p} onClick={() => onPanelChange(p)}
                className={`px-4 py-2 text-xs font-medium transition-colors ${panel === p ? 'border-b-2 border-blue-600 text-blue-600' : 'text-gray-500 hover:text-gray-700'}`}>
                {p === 'info' ? 'Info' : p === 'llm' ? 'LLM Config' : 'Data Retention'}
              </button>
            ))}
          </div>

          <div className="p-4">
            {panel === 'info' && (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm text-gray-600">
                <div><span className="font-medium text-gray-700">ID:</span> <span className="font-mono text-xs">{tenant.id}</span></div>
                <div><span className="font-medium text-gray-700">OIDC:</span> {tenant.oidcEnabled ? `Enabled (${tenant.oidcProvider})` : 'Disabled'}</div>
                <div><span className="font-medium text-gray-700">Created:</span> {new Date(tenant.createdAt).toLocaleDateString()}</div>
              </div>
            )}

            {panel === 'llm' && (
              <div className="space-y-3">
                {!llmConfig ? (
                  <div className="text-center py-4"><div className="animate-spin h-5 w-5 border-b-2 border-blue-600 rounded-full mx-auto" /></div>
                ) : (
                  <>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                      <div>
                        <label className="text-xs text-gray-500">Chat Provider</label>
                        <select className={`${inputCls} mt-1`} value={llmConfig.chatProvider}
                          onChange={e => setLlmConfig(c => c ? { ...c, chatProvider: e.target.value } : c)}>
                          <option value="openai">OpenAI</option>
                          <option value="anthropic">Anthropic</option>
                          <option value="azure_openai">Azure OpenAI</option>
                        </select>
                      </div>
                      <div>
                        <label className="text-xs text-gray-500">Chat Model</label>
                        <input className={`${inputCls} mt-1`} value={llmConfig.chatModel}
                          onChange={e => setLlmConfig(c => c ? { ...c, chatModel: e.target.value } : c)} />
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <input type="checkbox" id="routing" checked={llmConfig.routingEnabled}
                        onChange={e => setLlmConfig(c => c ? { ...c, routingEnabled: e.target.checked } : c)} />
                      <label htmlFor="routing" className="text-sm text-gray-700">Enable smart routing (simple → cheap model, complex → powerful model)</label>
                    </div>
                    {llmConfig.routingEnabled && (
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                        <div>
                          <label className="text-xs text-gray-500">Simple queries model</label>
                          <input className={`${inputCls} mt-1`} value={llmConfig.simpleModel}
                            onChange={e => setLlmConfig(c => c ? { ...c, simpleModel: e.target.value } : c)} />
                        </div>
                        <div>
                          <label className="text-xs text-gray-500">Complex queries model</label>
                          <input className={`${inputCls} mt-1`} value={llmConfig.complexModel}
                            onChange={e => setLlmConfig(c => c ? { ...c, complexModel: e.target.value } : c)} />
                        </div>
                      </div>
                    )}
                    <button onClick={saveLLM} disabled={saving}
                      className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 text-white text-xs rounded-lg hover:bg-blue-700 disabled:opacity-50">
                      {saving ? <RefreshCw size={12} className="animate-spin" /> : <Save size={12} />} Save LLM Config
                    </button>
                  </>
                )}
              </div>
            )}

            {panel === 'retention' && (
              <div className="space-y-3">
                {!retention ? (
                  <div className="text-center py-4"><div className="animate-spin h-5 w-5 border-b-2 border-blue-600 rounded-full mx-auto" /></div>
                ) : (
                  <>
                    <p className="text-xs text-gray-500">Configure how long data is retained before automatic deletion.</p>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      {([
                        { key: 'queryLogDays', label: 'Query logs (days)' },
                        { key: 'chatSessionDays', label: 'Chat sessions (days)' },
                        { key: 'auditLogDays', label: 'Audit logs (days)' },
                        { key: 'feedbackDays', label: 'Feedback (days)' },
                      ] as const).map(({ key, label }) => (
                        <div key={key}>
                          <label className="text-xs text-gray-500">{label}</label>
                          <input type="number" className={`${inputCls} mt-1`}
                            value={retention[key]}
                            onChange={e => setRetention(r => r ? { ...r, [key]: +e.target.value } : r)} />
                        </div>
                      ))}
                    </div>
                    <button onClick={saveRetention} disabled={saving}
                      className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 text-white text-xs rounded-lg hover:bg-blue-700 disabled:opacity-50">
                      {saving ? <RefreshCw size={12} className="animate-spin" /> : <Save size={12} />} Save Policy
                    </button>
                  </>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
