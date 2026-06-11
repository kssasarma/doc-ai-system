import React, { useEffect, useState } from 'react';
import { Building2, Plus, Settings, Save, RefreshCw, ChevronDown, ChevronUp } from 'lucide-react';
import {
  listTenants, createTenant, updateTenant,
  getTenantLLMConfig, updateTenantLLMConfig,
  getRetentionPolicy, updateRetentionPolicy,
  type Tenant, type TenantLLMConfig, type DataRetentionPolicy,
} from '../../services/tenantService';
import { useAuth } from '../../context/AuthContext';

export default function TenantManagementTab() {
  const { token } = useAuth();
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [activePanel, setActivePanel] = useState<Record<string, 'info' | 'llm' | 'retention'>>({});
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({ name: '', slug: '', plan: 'FREE', maxUsers: 10, maxDocuments: 100 });
  const [msg, setMsg] = useState<string | null>(null);

  useEffect(() => { load(); }, []);

  const load = async () => {
    if (!token) return;
    setLoading(true);
    const data = await listTenants(token);
    setTenants(data);
    setLoading(false);
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || !form.name || !form.slug) return;
    setCreating(true);
    setMsg(null);
    try {
      const t = await createTenant(token, form);
      setTenants(prev => [...prev, t]);
      setForm({ name: '', slug: '', plan: 'FREE', maxUsers: 10, maxDocuments: 100 });
      setMsg('Tenant created successfully.');
    } catch {
      setMsg('Failed to create tenant.');
    }
    setCreating(false);
  };

  const toggle = (id: string) => {
    setExpandedId(prev => prev === id ? null : id);
    setActivePanel(prev => ({ ...prev, [id]: prev[id] ?? 'info' }));
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-gray-800 mb-1">Tenant Management</h2>
        <p className="text-sm text-gray-500">Manage tenant isolation, LLM provider config, and data retention policies.</p>
      </div>

      {/* Create tenant */}
      <div className="bg-blue-50 border border-blue-100 rounded-xl p-4">
        <div className="flex items-center gap-2 mb-3 text-sm font-medium text-blue-800">
          <Plus size={14} /> Create new tenant
        </div>
        <form onSubmit={handleCreate} className="grid grid-cols-2 gap-2">
          <input className="col-span-2 sm:col-span-1 input-sm" placeholder="Name" value={form.name}
            onChange={e => setForm(f => ({ ...f, name: e.target.value }))} />
          <input className="col-span-2 sm:col-span-1 input-sm" placeholder="Slug (e.g. acme-corp)" value={form.slug}
            onChange={e => setForm(f => ({ ...f, slug: e.target.value }))} />
          <select className="input-sm" value={form.plan} onChange={e => setForm(f => ({ ...f, plan: e.target.value }))}>
            <option>FREE</option><option>PRO</option><option>ENTERPRISE</option>
          </select>
          <input type="number" className="input-sm" placeholder="Max users" value={form.maxUsers}
            onChange={e => setForm(f => ({ ...f, maxUsers: +e.target.value }))} />
          <button type="submit" disabled={creating}
            className="col-span-2 flex justify-center items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 disabled:opacity-50">
            {creating ? <RefreshCw size={14} className="animate-spin" /> : <Plus size={14} />} Create
          </button>
        </form>
        {msg && <p className="text-xs mt-2 text-blue-700">{msg}</p>}
      </div>

      {loading && <div className="flex justify-center py-8"><div className="animate-spin h-7 w-7 border-b-2 border-blue-600 rounded-full" /></div>}

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
    const updated = await updateTenantLLMConfig(token, tenant.id, llmConfig);
    setLlmConfig(updated);
    setSaving(false);
  };

  const saveRetention = async () => {
    if (!retention) return;
    setSaving(true);
    const updated = await updateRetentionPolicy(token, tenant.id, retention);
    setRetention(updated);
    setSaving(false);
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
        <button onClick={onToggle} className="flex-1 text-left">
          <div className="flex items-center gap-2">
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
        <button onClick={onToggle} className="p-1.5 text-gray-400 hover:text-gray-600">
          {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
        </button>
      </div>

      {expanded && (
        <div className="border-t border-gray-100">
          {/* Panel tabs */}
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
              <div className="grid grid-cols-2 gap-3 text-sm text-gray-600">
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
                    <div className="grid grid-cols-2 gap-2">
                      <div>
                        <label className="text-xs text-gray-500">Chat Provider</label>
                        <select className="input-sm mt-1 w-full" value={llmConfig.chatProvider}
                          onChange={e => setLlmConfig(c => c ? { ...c, chatProvider: e.target.value } : c)}>
                          <option value="openai">OpenAI</option>
                          <option value="anthropic">Anthropic</option>
                          <option value="azure_openai">Azure OpenAI</option>
                        </select>
                      </div>
                      <div>
                        <label className="text-xs text-gray-500">Chat Model</label>
                        <input className="input-sm mt-1 w-full" value={llmConfig.chatModel}
                          onChange={e => setLlmConfig(c => c ? { ...c, chatModel: e.target.value } : c)} />
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <input type="checkbox" id="routing" checked={llmConfig.routingEnabled}
                        onChange={e => setLlmConfig(c => c ? { ...c, routingEnabled: e.target.checked } : c)} />
                      <label htmlFor="routing" className="text-sm text-gray-700">Enable smart routing (simple → cheap model, complex → powerful model)</label>
                    </div>
                    {llmConfig.routingEnabled && (
                      <div className="grid grid-cols-2 gap-2">
                        <div>
                          <label className="text-xs text-gray-500">Simple queries model</label>
                          <input className="input-sm mt-1 w-full" value={llmConfig.simpleModel}
                            onChange={e => setLlmConfig(c => c ? { ...c, simpleModel: e.target.value } : c)} />
                        </div>
                        <div>
                          <label className="text-xs text-gray-500">Complex queries model</label>
                          <input className="input-sm mt-1 w-full" value={llmConfig.complexModel}
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
                    <div className="grid grid-cols-2 gap-3">
                      {[
                        { key: 'queryLogDays', label: 'Query logs (days)' },
                        { key: 'chatSessionDays', label: 'Chat sessions (days)' },
                        { key: 'auditLogDays', label: 'Audit logs (days)' },
                        { key: 'feedbackDays', label: 'Feedback (days)' },
                      ].map(({ key, label }) => (
                        <div key={key}>
                          <label className="text-xs text-gray-500">{label}</label>
                          <input type="number" className="input-sm mt-1 w-full"
                            value={(retention as any)[key]}
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
