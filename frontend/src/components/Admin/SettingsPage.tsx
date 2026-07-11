import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { Save, AlertCircle, Sparkles, Clock, Palette } from 'lucide-react';
import {
  getTenantLLMConfig, updateTenantLLMConfig,
  getRetentionPolicy, updateRetentionPolicy,
  type TenantLLMConfig, type DataRetentionPolicy,
} from '../../services/tenantService';
import { getTenantBranding, updateBranding, type TenantBranding } from '../../services/brandingService';
import { useAuth } from '../../context/AuthContext';
import { fadeInUp, staggerContainer } from '../../lib/motion';
import PageHeader from '../ui/PageHeader';
import { Card, CardBody } from '../ui/Card';
import Button from '../ui/Button';
import Input from '../ui/Input';
import Select from '../ui/Select';
import { SkeletonCard } from '../ui/Skeleton';

type Section = 'llm' | 'retention' | 'branding';

function SectionCard({ title, icon: Icon, children }: { title: string; icon: React.ElementType; children: React.ReactNode }) {
  return (
    <Card>
      <CardBody>
        <h3 className="text-sm font-semibold text-foreground flex items-center gap-2 mb-4">
          <Icon size={16} className="text-primary" /> {title}
        </h3>
        {children}
      </CardBody>
    </Card>
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
    return (
      <div>
        <PageHeader title="Tenant Settings" description="Configure your organization's LLM provider, data retention, and branding." />
        <div className="space-y-6">
          <SkeletonCard className="h-64" />
          <SkeletonCard className="h-48" />
          <SkeletonCard className="h-72" />
        </div>
      </div>
    );
  }
  if (error) {
    return (
      <div>
        <PageHeader title="Tenant Settings" description="Configure your organization's LLM provider, data retention, and branding." />
        <div className="p-6 text-center text-danger flex items-center justify-center gap-2"><AlertCircle className="w-5 h-5" />{error}</div>
      </div>
    );
  }

  return (
    <div>
      <PageHeader title="Tenant Settings" description="Configure your organization's LLM provider, data retention, and branding." />

      <motion.div variants={staggerContainer} initial="hidden" animate="visible" className="space-y-6">
        {llmConfig && (
          <motion.div variants={fadeInUp}>
            <SectionCard title="LLM Configuration" icon={Sparkles}>
              <div className="space-y-3">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <Select
                    label="Chat Provider"
                    value={llmConfig.chatProvider}
                    onChange={e => setLlmConfig(c => c ? { ...c, chatProvider: e.target.value } : c)}
                  >
                    <option value="openai">OpenAI</option>
                    <option value="anthropic">Anthropic</option>
                    <option value="azure_openai">Azure OpenAI</option>
                  </Select>
                  <Input
                    label="Chat Model"
                    value={llmConfig.chatModel}
                    onChange={e => setLlmConfig(c => c ? { ...c, chatModel: e.target.value } : c)}
                  />
                  <Input
                    label="Embedding Provider"
                    value={llmConfig.embeddingProvider}
                    onChange={e => setLlmConfig(c => c ? { ...c, embeddingProvider: e.target.value } : c)}
                  />
                  <Input
                    label="Embedding Model"
                    value={llmConfig.embeddingModel}
                    onChange={e => setLlmConfig(c => c ? { ...c, embeddingModel: e.target.value } : c)}
                  />
                </div>
                <div className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    id="routing"
                    checked={llmConfig.routingEnabled}
                    onChange={e => setLlmConfig(c => c ? { ...c, routingEnabled: e.target.checked } : c)}
                    className="h-4 w-4 rounded border-border accent-primary"
                  />
                  <label htmlFor="routing" className="text-sm text-foreground">Enable smart routing (simple → cheap model, complex → powerful model)</label>
                </div>
                {llmConfig.routingEnabled && (
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <Input
                      label="Simple queries model"
                      value={llmConfig.simpleModel}
                      onChange={e => setLlmConfig(c => c ? { ...c, simpleModel: e.target.value } : c)}
                    />
                    <Input
                      label="Complex queries model"
                      value={llmConfig.complexModel}
                      onChange={e => setLlmConfig(c => c ? { ...c, complexModel: e.target.value } : c)}
                    />
                  </div>
                )}
                <div className="flex items-center gap-3">
                  <Button onClick={saveLLM} loading={saving === 'llm'} leftIcon={<Save size={14} />}>Save</Button>
                  {saved === 'llm' && <span className="text-xs text-success">Saved.</span>}
                </div>
              </div>
            </SectionCard>
          </motion.div>
        )}

        {retention && (
          <motion.div variants={fadeInUp}>
            <SectionCard title="Data Retention" icon={Clock}>
              <div className="space-y-3">
                <p className="text-xs text-muted-foreground">Configure how long data is retained before automatic deletion.</p>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {([
                    { key: 'queryLogDays', label: 'Query logs (days)' },
                    { key: 'chatSessionDays', label: 'Chat sessions (days)' },
                    { key: 'auditLogDays', label: 'Audit logs (days)' },
                    { key: 'feedbackDays', label: 'Feedback (days)' },
                  ] as const).map(({ key, label }) => (
                    <Input
                      key={key}
                      type="number"
                      min={1}
                      label={label}
                      value={retention[key]}
                      onChange={e => setRetention(r => r ? { ...r, [key]: +e.target.value } : r)}
                    />
                  ))}
                </div>
                <div className="flex items-center gap-3">
                  <Button onClick={saveRetention} loading={saving === 'retention'} leftIcon={<Save size={14} />}>Save</Button>
                  {saved === 'retention' && <span className="text-xs text-success">Saved.</span>}
                </div>
              </div>
            </SectionCard>
          </motion.div>
        )}

        {branding && (
          <motion.div variants={fadeInUp}>
            <SectionCard title="Branding" icon={Palette}>
              <div className="space-y-3">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <Input
                    label="Product name"
                    value={branding.productName}
                    onChange={e => setBranding(b => b ? { ...b, productName: e.target.value } : b)}
                  />
                  <Input
                    label="Support email"
                    type="email"
                    value={branding.supportEmail ?? ''}
                    onChange={e => setBranding(b => b ? { ...b, supportEmail: e.target.value } : b)}
                  />
                  <Input
                    label="Logo URL"
                    value={branding.logoUrl ?? ''}
                    onChange={e => setBranding(b => b ? { ...b, logoUrl: e.target.value } : b)}
                  />
                  <Input
                    label="Favicon URL"
                    value={branding.faviconUrl ?? ''}
                    onChange={e => setBranding(b => b ? { ...b, faviconUrl: e.target.value } : b)}
                  />
                  <div>
                    <label className="block text-sm font-medium text-foreground mb-1">Primary color</label>
                    <div className="flex items-center gap-2">
                      <input
                        type="color"
                        className="h-9 w-12 rounded border border-border"
                        value={branding.primaryColor}
                        onChange={e => setBranding(b => b ? { ...b, primaryColor: e.target.value } : b)}
                      />
                      <Input
                        className="flex-1"
                        value={branding.primaryColor}
                        onChange={e => setBranding(b => b ? { ...b, primaryColor: e.target.value } : b)}
                      />
                    </div>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-foreground mb-1">Accent color</label>
                    <div className="flex items-center gap-2">
                      <input
                        type="color"
                        className="h-9 w-12 rounded border border-border"
                        value={branding.accentColor}
                        onChange={e => setBranding(b => b ? { ...b, accentColor: e.target.value } : b)}
                      />
                      <Input
                        className="flex-1"
                        value={branding.accentColor}
                        onChange={e => setBranding(b => b ? { ...b, accentColor: e.target.value } : b)}
                      />
                    </div>
                  </div>
                </div>
                <Input
                  label="Footer text"
                  value={branding.footerText ?? ''}
                  onChange={e => setBranding(b => b ? { ...b, footerText: e.target.value } : b)}
                />
                <div className="flex items-center gap-3">
                  <Button onClick={saveBranding} loading={saving === 'branding'} leftIcon={<Save size={14} />}>Save</Button>
                  {saved === 'branding' && <span className="text-xs text-success">Saved.</span>}
                </div>
              </div>
            </SectionCard>
          </motion.div>
        )}
      </motion.div>
    </div>
  );
}
