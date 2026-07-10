import React, { useEffect, useState } from 'react';
import { Save, RefreshCw, AlertCircle, Sparkles, Clock, Palette } from 'lucide-react';
import {
  getTenantLLMConfig, updateTenantLLMConfig,
  getRetentionPolicy, updateRetentionPolicy,
  type TenantLLMConfig, type DataRetentionPolicy,
} from '../../services/tenantService';
import { getTenantBranding, updateBranding, type TenantBranding } from '../../services/brandingService';
import { useAuth } from '../../context/AuthContext';

const inputCls = 'w-full text-sm border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500';

type Section = 'llm' | 'retention' | 'branding';

function SectionCard({ title, icon: Icon, children }: { title: string; icon: React.ElementType; children: React.ReactNode }) {
  return (
    <div className="bg-white border border-gray-200 rounded-xl p-5">
      <h3 className="text-sm font-semibold text-gray-800 flex items-center gap-2 mb-4">
        <Icon size={16} className="text-blue-600" /> {title}
      </h3>
      {children}
    </div>
  );
}

export default function SettingsPage() {
  const { token, user } = useAuth();
  const tenantId = user?.tenantId ?? '';

  const [llmConfig, setLlmConfig] = useState<TenantLLMConfig | null>(null);
  const [retention, setRetention] = useState<DataRetentionPolicy | null>(null);
  const [branding, setBranding] = useState<TenantBranding | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState<Section | null>(null);
  const [saved, setSaved] = useState<Section | null>(null);

  useEffect(() => {
    if (!token || !tenantId) return;
    setLoading(true);
    setError('');
    Promise.all([
      getTenantLLMConfig(token, tenantId),
      getRetentionPolicy(token, tenantId),
      getTenantBranding(token, tenantId),
    ])
      .then(([llm, ret, brand]) => {
        setLlmConfig(llm);
        setRetention(ret);
        setBranding(brand);
      })
      .catch(e => setError(e instanceof Error ? e.message : 'Failed to load tenant settings'))
      .finally(() => setLoading(false));
  }, [token, tenantId]);

  const flashSaved = (section: Section) => {
    setSaved(section);
    setTimeout(() => setSaved(prev => (prev === section ? null : prev)), 2500);
  };

  const saveLLM = async () => {
    if (!llmConfig || !token) return;
    setSaving('llm');
    try {
      setLlmConfig(await updateTenantLLMConfig(token, tenantId, llmConfig));
      flashSaved('llm');
    } finally {
      setSaving(null);
    }
  };

  const saveRetention = async () => {
    if (!retention || !token) return;
    setSaving('retention');
    try {
      setRetention(await updateRetentionPolicy(token, tenantId, retention));
      flashSaved('retention');
    } finally {
      setSaving(null);
    }
  };

  const saveBranding = async () => {
    if (!branding || !token) return;
    setSaving('branding');
    try {
      setBranding(await updateBranding(tenantId, branding, token));
      flashSaved('branding');
    } finally {
      setSaving(null);
    }
  };

  if (loading) {
    return <div className="flex justify-center py-12"><div className="animate-spin h-7 w-7 border-b-2 border-blue-600 rounded-full" /></div>;
  }
  if (error) {
    return <div className="p-6 text-center text-red-500 flex items-center justify-center gap-2"><AlertCircle className="w-5 h-5" />{error}</div>;
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-gray-800 mb-1">Tenant Settings</h2>
        <p className="text-sm text-gray-500">Configure your organization's LLM provider, data retention, and branding.</p>
      </div>

      {llmConfig && (
        <SectionCard title="LLM Configuration" icon={Sparkles}>
          <div className="space-y-3">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
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
              <div>
                <label className="text-xs text-gray-500">Embedding Provider</label>
                <input className={`${inputCls} mt-1`} value={llmConfig.embeddingProvider}
                  onChange={e => setLlmConfig(c => c ? { ...c, embeddingProvider: e.target.value } : c)} />
              </div>
              <div>
                <label className="text-xs text-gray-500">Embedding Model</label>
                <input className={`${inputCls} mt-1`} value={llmConfig.embeddingModel}
                  onChange={e => setLlmConfig(c => c ? { ...c, embeddingModel: e.target.value } : c)} />
              </div>
            </div>
            <div className="flex items-center gap-2">
              <input type="checkbox" id="routing" checked={llmConfig.routingEnabled}
                onChange={e => setLlmConfig(c => c ? { ...c, routingEnabled: e.target.checked } : c)} />
              <label htmlFor="routing" className="text-sm text-gray-700">Enable smart routing (simple → cheap model, complex → powerful model)</label>
            </div>
            {llmConfig.routingEnabled && (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
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
            <div className="flex items-center gap-3">
              <button onClick={saveLLM} disabled={saving === 'llm'}
                className="flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 disabled:opacity-50">
                {saving === 'llm' ? <RefreshCw size={14} className="animate-spin" /> : <Save size={14} />} Save
              </button>
              {saved === 'llm' && <span className="text-xs text-green-600">Saved.</span>}
            </div>
          </div>
        </SectionCard>
      )}

      {retention && (
        <SectionCard title="Data Retention" icon={Clock}>
          <div className="space-y-3">
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
                  <input type="number" min={1} className={`${inputCls} mt-1`}
                    value={retention[key]}
                    onChange={e => setRetention(r => r ? { ...r, [key]: +e.target.value } : r)} />
                </div>
              ))}
            </div>
            <div className="flex items-center gap-3">
              <button onClick={saveRetention} disabled={saving === 'retention'}
                className="flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 disabled:opacity-50">
                {saving === 'retention' ? <RefreshCw size={14} className="animate-spin" /> : <Save size={14} />} Save
              </button>
              {saved === 'retention' && <span className="text-xs text-green-600">Saved.</span>}
            </div>
          </div>
        </SectionCard>
      )}

      {branding && (
        <SectionCard title="Branding" icon={Palette}>
          <div className="space-y-3">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-gray-500">Product name</label>
                <input className={`${inputCls} mt-1`} value={branding.productName}
                  onChange={e => setBranding(b => b ? { ...b, productName: e.target.value } : b)} />
              </div>
              <div>
                <label className="text-xs text-gray-500">Support email</label>
                <input type="email" className={`${inputCls} mt-1`} value={branding.supportEmail ?? ''}
                  onChange={e => setBranding(b => b ? { ...b, supportEmail: e.target.value } : b)} />
              </div>
              <div>
                <label className="text-xs text-gray-500">Logo URL</label>
                <input className={`${inputCls} mt-1`} value={branding.logoUrl ?? ''}
                  onChange={e => setBranding(b => b ? { ...b, logoUrl: e.target.value } : b)} />
              </div>
              <div>
                <label className="text-xs text-gray-500">Favicon URL</label>
                <input className={`${inputCls} mt-1`} value={branding.faviconUrl ?? ''}
                  onChange={e => setBranding(b => b ? { ...b, faviconUrl: e.target.value } : b)} />
              </div>
              <div>
                <label className="text-xs text-gray-500">Primary color</label>
                <div className="flex items-center gap-2 mt-1">
                  <input type="color" className="h-9 w-12 rounded border border-gray-300" value={branding.primaryColor}
                    onChange={e => setBranding(b => b ? { ...b, primaryColor: e.target.value } : b)} />
                  <input className={inputCls} value={branding.primaryColor}
                    onChange={e => setBranding(b => b ? { ...b, primaryColor: e.target.value } : b)} />
                </div>
              </div>
              <div>
                <label className="text-xs text-gray-500">Accent color</label>
                <div className="flex items-center gap-2 mt-1">
                  <input type="color" className="h-9 w-12 rounded border border-gray-300" value={branding.accentColor}
                    onChange={e => setBranding(b => b ? { ...b, accentColor: e.target.value } : b)} />
                  <input className={inputCls} value={branding.accentColor}
                    onChange={e => setBranding(b => b ? { ...b, accentColor: e.target.value } : b)} />
                </div>
              </div>
            </div>
            <div>
              <label className="text-xs text-gray-500">Footer text</label>
              <input className={`${inputCls} mt-1`} value={branding.footerText ?? ''}
                onChange={e => setBranding(b => b ? { ...b, footerText: e.target.value } : b)} />
            </div>
            <div className="flex items-center gap-3">
              <button onClick={saveBranding} disabled={saving === 'branding'}
                className="flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 disabled:opacity-50">
                {saving === 'branding' ? <RefreshCw size={14} className="animate-spin" /> : <Save size={14} />} Save
              </button>
              {saved === 'branding' && <span className="text-xs text-green-600">Saved.</span>}
            </div>
          </div>
        </SectionCard>
      )}
    </div>
  );
}
