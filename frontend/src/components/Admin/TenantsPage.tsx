import React, { useEffect, useState } from 'react';
import { Building2, Plus, Save, ChevronDown, ChevronUp, AlertCircle } from 'lucide-react';
import { motion } from 'framer-motion';
import {
  listTenants, createTenant, updateTenant,
  getTenantLLMConfig,
  getRetentionPolicy, updateRetentionPolicy,
  type Tenant, type TenantLLMConfig, type DataRetentionPolicy,
} from '../../services/tenantService';
import { useAuth } from '../../context/AuthContext';
import PageHeader from '../ui/PageHeader';
import { Card } from '../ui/Card';
import Badge from '../ui/Badge';
import Button from '../ui/Button';
import IconButton from '../ui/IconButton';
import EmptyState from '../ui/EmptyState';
import Spinner from '../ui/Spinner';
import { SkeletonCard } from '../ui/Skeleton';
import Input from '../ui/Input';
import Select from '../ui/Select';
import { useToast } from '../ui/Toast';
import { fadeInUp, staggerContainer } from '../../lib/motion';
import { cn } from '../../lib/cn';
import LlmConfigForm from './LlmConfigForm';

const EMPTY_FORM = { name: '', slug: '', plan: 'FREE', maxUsers: 10, maxDocuments: 100, adminEmail: '' };

export default function TenantsPage() {
  const { token } = useAuth();
  const toast = useToast();
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [activePanel, setActivePanel] = useState<Record<string, 'info' | 'llm' | 'retention'>>({});
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);

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
    try {
      const t = await createTenant(token, form);
      setTenants(prev => [...prev, t]);
      setForm(EMPTY_FORM);
      toast.success(`Tenant "${t.name}" created. An invitation was emailed to ${form.adminEmail}.`);
    } catch (e) {
      const detail = (e as { response?: { data?: { error?: string } } })?.response?.data?.error;
      toast.error(detail || 'Failed to create tenant.');
    } finally {
      setCreating(false);
    }
  };

  const toggle = (id: string) => {
    setExpandedId(prev => prev === id ? null : id);
    setActivePanel(prev => ({ ...prev, [id]: prev[id] ?? 'info' }));
  };

  return (
    <motion.div variants={staggerContainer} initial="hidden" animate="visible" className="space-y-6">
      <PageHeader title="Tenant Management" description="Create tenants and manage isolation, LLM provider config, and data retention." />

      {/* Create tenant */}
      <motion.div variants={fadeInUp} className="bg-primary/10 border border-primary/20 rounded-xl p-4">
        <div className="flex items-center gap-2 mb-3 text-sm font-medium text-primary">
          <Plus size={14} /> Create new tenant
        </div>
        <form onSubmit={handleCreate} className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <Input id="tenant-name" label="Name *" placeholder="Acme Corp" value={form.name}
            onChange={e => setForm(f => ({ ...f, name: e.target.value }))} required />
          <Input id="tenant-slug" label="Slug *" placeholder="acme-corp" value={form.slug}
            onChange={e => setForm(f => ({ ...f, slug: e.target.value }))} required />
          <div className="sm:col-span-2">
            <Input id="tenant-admin-email" type="email" label="Admin email *" placeholder="admin@acme.com" value={form.adminEmail}
              onChange={e => setForm(f => ({ ...f, adminEmail: e.target.value }))} required
              hint="This person is invited as the tenant's first admin." />
          </div>
          <Select id="tenant-plan" label="Plan" value={form.plan} onChange={e => setForm(f => ({ ...f, plan: e.target.value }))}>
            <option>FREE</option><option>PRO</option><option>ENTERPRISE</option>
          </Select>
          <Input id="tenant-max-users" type="number" label="Max users" min={1} value={form.maxUsers}
            onChange={e => setForm(f => ({ ...f, maxUsers: +e.target.value }))} />
          <Button type="submit" variant="primary" disabled={creating} loading={creating}
            leftIcon={<Plus size={14} />} className="sm:col-span-2">
            Create & invite admin
          </Button>
        </form>
      </motion.div>

      {loading ? (
        <div className="space-y-3">
          <SkeletonCard /><SkeletonCard /><SkeletonCard />
        </div>
      ) : loadError ? (
        <div className="p-6 text-center text-danger flex items-center justify-center gap-2"><AlertCircle className="w-5 h-5" />{loadError}</div>
      ) : tenants.length === 0 ? (
        <Card>
          <EmptyState icon={Building2} title="No tenants yet" description="Create the first one above." />
        </Card>
      ) : (
        <motion.div variants={fadeInUp} className="space-y-3">
          {tenants.map(t => (
            <TenantCard key={t.id} tenant={t} token={token!}
              expanded={expandedId === t.id}
              panel={activePanel[t.id] ?? 'info'}
              onToggle={() => toggle(t.id)}
              onPanelChange={p => setActivePanel(prev => ({ ...prev, [t.id]: p }))}
              onUpdated={updated => setTenants(prev => prev.map(x => x.id === updated.id ? updated : x))}
            />
          ))}
        </motion.div>
      )}
    </motion.div>
  );
}

function TenantCard({ tenant, token, expanded, panel, onToggle, onPanelChange, onUpdated }: {
  tenant: Tenant; token: string; expanded: boolean;
  panel: 'info' | 'llm' | 'retention';
  onToggle: () => void;
  onPanelChange: (p: 'info' | 'llm' | 'retention') => void;
  onUpdated: (t: Tenant) => void;
}) {
  const toast = useToast();
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

  const saveRetention = async () => {
    if (!retention) return;
    setSaving(true);
    try {
      const updated = await updateRetentionPolicy(token, tenant.id, retention);
      setRetention(updated);
      toast.success('Retention policy saved.');
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
      toast.success(updated.active ? `${updated.name} activated.` : `${updated.name} deactivated.`);
    } finally {
      setTogglingActive(false);
    }
  };

  const planBadge: Record<string, 'neutral' | 'primary' | 'info'> = {
    FREE: 'neutral',
    PRO: 'primary',
    ENTERPRISE: 'info',
  };

  return (
    <Card className="overflow-hidden">
      <div className="flex items-center gap-3 p-4">
        <Building2 size={16} className="text-primary flex-shrink-0" />
        <button onClick={onToggle} className="flex-1 text-left min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm font-medium text-foreground">{tenant.name}</span>
            <span className="text-xs text-muted-foreground">/{tenant.slug}</span>
            <Badge variant={planBadge[tenant.plan] ?? 'neutral'}>{tenant.plan}</Badge>
            {!tenant.active && <Badge variant="danger">Inactive</Badge>}
          </div>
          <div className="text-xs text-muted-foreground mt-0.5">
            {tenant.maxUsers} users · {tenant.maxDocuments} docs
          </div>
        </button>
        <Button
          variant={tenant.active ? 'danger' : 'outline'}
          size="sm"
          onClick={toggleActive}
          disabled={togglingActive}
          loading={togglingActive}
        >
          {tenant.active ? 'Deactivate' : 'Activate'}
        </Button>
        <IconButton
          label={expanded ? 'Collapse tenant details' : 'Expand tenant details'}
          variant="ghost"
          size="sm"
          onClick={onToggle}
        >
          {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
        </IconButton>
      </div>

      {expanded && (
        <div className="border-t border-border">
          <div className="flex border-b border-border">
            {(['info', 'llm', 'retention'] as const).map(p => (
              <button key={p} onClick={() => onPanelChange(p)}
                className={cn(
                  'px-4 py-2 text-xs font-medium transition-colors',
                  panel === p ? 'border-b-2 border-primary text-primary' : 'text-muted-foreground hover:text-foreground',
                )}>
                {p === 'info' ? 'Info' : p === 'llm' ? 'LLM Config' : 'Data Retention'}
              </button>
            ))}
          </div>

          <div className="p-4">
            {panel === 'info' && (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm text-muted-foreground">
                <div><span className="font-medium text-foreground">ID:</span> <span className="font-mono text-xs">{tenant.id}</span></div>
                <div><span className="font-medium text-foreground">OIDC:</span> {tenant.oidcEnabled ? `Enabled (${tenant.oidcProvider})` : 'Disabled'}</div>
                <div><span className="font-medium text-foreground">Created:</span> {new Date(tenant.createdAt).toLocaleDateString()}</div>
              </div>
            )}

            {panel === 'llm' && (
              <div className="space-y-3">
                {!llmConfig ? (
                  <div className="text-center py-4"><Spinner size="md" /></div>
                ) : (
                  <LlmConfigForm token={token} tenantId={tenant.id} config={llmConfig} onSaved={setLlmConfig} size="sm" />
                )}
              </div>
            )}

            {panel === 'retention' && (
              <div className="space-y-3">
                {!retention ? (
                  <div className="text-center py-4"><Spinner size="md" /></div>
                ) : (
                  <>
                    <p className="text-xs text-muted-foreground">Configure how long data is retained before automatic deletion.</p>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      {([
                        { key: 'queryLogDays', label: 'Query logs (days)' },
                        { key: 'chatSessionDays', label: 'Chat sessions (days)' },
                        { key: 'auditLogDays', label: 'Audit logs (days)' },
                        { key: 'feedbackDays', label: 'Feedback (days)' },
                      ] as const).map(({ key, label }) => (
                        <Input key={key} type="number" label={label}
                          value={retention[key]}
                          onChange={e => setRetention(r => r ? { ...r, [key]: +e.target.value } : r)} />
                      ))}
                    </div>
                    <Button variant="primary" size="sm" onClick={saveRetention} disabled={saving} loading={saving} leftIcon={<Save size={12} />}>
                      Save Policy
                    </Button>
                  </>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </Card>
  );
}
